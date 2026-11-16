package com.handmotionblur;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.lang.reflect.Method;

/**
 * Y tuong:
 *  1. RenderHandEvent (khong cancel): bind FBO rieng -> vanilla/OptiFine ve tay vao do.
 *  2. RenderGameOverlayEvent.Pre(ALL): vanilla da bind lai FBO chinh sau renderWorld.
 *     Luc nay ta cap nhat buffer tich luy (ping-pong) roi ve len man hinh.
 */
public class HandBlurRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();

    private Framebuffer hand;
    private final Framebuffer[] accum = new Framebuffer[2];
    private int cur = 0;
    private boolean valid = false;     // accum[cur] da chua du lieu hop le
    private boolean usedFbo = false;   // frame nay tay da duoc ve vao FBO

    private long activeUntil = 0L;
    private long lastNano = 0L;
    private Item lastItem;
    private int lastSlot = -1;

    private boolean ofLookedUp = false;
    private Method ofIsShaders;

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent e) {
        usedFbo = false;
        if (!shouldRun()) {
            valid = false;
            return;
        }
        if (!ensureBuffers()) return;
        hand.framebufferClear();       // (0,0,0,0)
        hand.bindFramebuffer(true);    // vanilla se clear depth roi ve tay vao day
        usedFbo = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onOverlay(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !usedFbo) return;
        usedFbo = false;

        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016f : (now - lastNano) / 1.0e9f;
        lastNano = now;
        dt = Math.max(0.001f, Math.min(0.1f, dt));
        float tau = Math.max(0.005f, HandMotionBlur.blurMs / 1000.0f);

        setupState();
        try {
            Framebuffer prev = accum[cur];
            Framebuffer next = accum[1 - cur];

            next.bindFramebuffer(true);
            GlStateManager.disableBlend();

            if (!valid) {
                // frame dau tien: accum = tay hien tai (khong blur, tranh "hien dan")
                drawTex(hand, 1.0f);
                valid = true;
            } else if ("MAX".equals(HandMotionBlur.mode)) {
                float decay = (float) Math.exp(-dt / tau);
                drawTex(prev, decay);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                GL14.glBlendEquation(GL14.GL_MAX);
                drawTex(hand, 1.0f);
                GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            } else {
                float a = 1.0f - (float) Math.exp(-dt / tau);
                drawTex(prev, 1.0f - a);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                drawTex(hand, a);
            }
            cur = 1 - cur;

            // ghep len man hinh chinh (premultiplied alpha)
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawTex(accum[cur], 1.0f);
        } finally {
            restoreState();
        }
    }

    // ------------------------------------------------------------------ logic

    private boolean shouldRun() {
        if (!HandMotionBlur.enabled) return false;
        if (!OpenGlHelper.isFramebufferEnabled()) return false;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return false;
        if (mc.gameSettings.thirdPersonView != 0 || mc.gameSettings.hideGUI || mc.gameSettings.anaglyph) return false;
        if (shadersActive()) return false;

        ItemStack held = p.getHeldItem();
        Item it = held == null ? null : held.getItem();
        int slot = p.inventory.currentItem;
        boolean activity = HandMotionBlur.always
                || p.swingProgress > 0.0f || p.isSwingInProgress
                || p.isUsingItem()
                || it != lastItem || slot != lastSlot;
        lastItem = it;
        lastSlot = slot;

        long now = System.currentTimeMillis();
        if (activity) activeUntil = now + 250L + 6L * HandMotionBlur.blurMs;
        return now < activeUntil;
    }

    /** OptiFine shader pack tu quan ly hand FBO -> tat mod de tranh loi. */
    private boolean shadersActive() {
        if (!ofLookedUp) {
            ofLookedUp = true;
            try {
                ofIsShaders = Class.forName("Config").getMethod("isShaders");
            } catch (Throwable t) {
                ofIsShaders = null;
            }
        }
        if (ofIsShaders == null) return false;
        try {
            return (Boolean) ofIsShaders.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean ensureBuffers() {
        int w = mc.getFramebuffer().framebufferWidth;
        int h = mc.getFramebuffer().framebufferHeight;
        if (w <= 0 || h <= 0) return false;
        if (hand == null) {
            hand = new Framebuffer(w, h, true);
            hand.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
            accum[0] = new Framebuffer(w, h, false);
            accum[1] = new Framebuffer(w, h, false);
            accum[0].setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
            accum[1].setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
            valid = false;
        } else if (hand.framebufferWidth != w || hand.framebufferHeight != h) {
            hand.createBindFramebuffer(w, h);
            accum[0].createBindFramebuffer(w, h);
            accum[1].createBindFramebuffer(w, h);
            valid = false;
        }
        return true;
    }

    // ------------------------------------------------------------------ GL helpers

    private void setupState() {
        GlStateManager.matrixMode(5889); // PROJECTION
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(5888); // MODELVIEW
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableFog();
        GlStateManager.colorMask(true, true, true, true);
    }

    private void restoreState() {
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
    }

    /** Ve fullscreen quad voi texture cua FBO, nhan mau (premultiplied) voi f. */
    private void drawTex(Framebuffer src, float f) {
        src.bindFramebufferTexture();
        GlStateManager.color(f, f, f, f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f); GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glTexCoord2f(1.0f, 0.0f); GL11.glVertex2f(1.0f, -1.0f);
        GL11.glTexCoord2f(1.0f, 1.0f); GL11.glVertex2f(1.0f, 1.0f);
        GL11.glTexCoord2f(0.0f, 1.0f); GL11.glVertex2f(-1.0f, 1.0f);
        GL11.glEnd();
    }
}
