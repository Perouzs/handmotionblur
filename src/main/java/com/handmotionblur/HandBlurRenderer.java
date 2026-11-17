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
 * Luong moi frame:
 *  1. RenderHandEvent (khong cancel): neu can (hand blur hoac world blur dang bat) thi bind FBO rieng
 *     de tay duoc ve tach khoi the gioi.
 *  2. RenderGameOverlayEvent.Pre(ALL): luc nay framebuffer chinh chi chua the gioi.
 *       a) World pass: tron the gioi vao buffer tich luy roi ghi nguoc lai framebuffer chinh (chi RGB).
 *       b) Hand pass: tich luy tay (neu dang hoat dong) roi ve de len man hinh.
 */
public class HandBlurRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();

    private Framebuffer handFbo;
    private final Framebuffer[] handAccum = new Framebuffer[2];
    private final Framebuffer[] worldAccum = new Framebuffer[2];
    private int handCur = 0, worldCur = 0;
    private boolean handValid = false, worldValid = false;

    private boolean usedFbo = false;     // frame nay tay da duoc ve vao FBO
    private boolean handBlurFrame = false; // frame nay tay can tich luy

    private long activeUntil = 0L;
    private long lastNano = 0L;
    private Item lastItem;
    private int lastSlot = -1;
    private Object lastWorld;

    private boolean ofLookedUp = false;
    private Method ofIsShaders;

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent e) {
        usedFbo = false;
        handBlurFrame = false;

        boolean worldOn = worldActive();
        if (mc.gameSettings.hideGUI) worldValid = false; // overlay co the khong chay khi an GUI
        boolean handOn = handActive();
        if (!handOn) handValid = false;
        if (!handOn && !worldOn) return;
        if (!ensureBuffers()) return;

        handFbo.framebufferClear();       // (0,0,0,0)
        handFbo.bindFramebuffer(true);    // vanilla se clear depth roi ve tay vao day
        usedFbo = true;
        handBlurFrame = handOn;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onOverlay(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        boolean worldOn = worldActive();
        if (!usedFbo && !worldOn) return;
        boolean hadHand = usedFbo;
        usedFbo = false;

        if (!ensureBuffers()) return;

        long now = System.nanoTime();
        float raw = lastNano == 0L ? 0.016f : (now - lastNano) / 1.0e9f;
        lastNano = now;
        if (raw > 0.25f) worldValid = false; // gap lon (pause, load...) -> khoi tao lai
        float dt = Math.max(0.001f, Math.min(0.1f, raw));

        setupState();
        try {
            if (worldOn) worldPass(dt);
            if (hadHand) handPass(dt);
        } finally {
            restoreState();
        }
    }

    // ------------------------------------------------------------------ passes

    private void worldPass(float dt) {
        BlurSettings s = HandMotionBlur.world;
        Framebuffer main = mc.getFramebuffer();
        Framebuffer prev = worldAccum[worldCur];
        Framebuffer next = worldAccum[1 - worldCur];

        next.bindFramebuffer(true);
        GlStateManager.colorMask(true, true, true, false); // chi RGB: alpha cua the gioi khong dang tin
        GlStateManager.disableBlend();

        if (!worldValid) {
            drawTex(main, 1.0f);
            worldValid = true;
        } else if (s.isMax()) {
            drawTex(prev, s.decay(dt));
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            GL14.glBlendEquation(GL14.GL_MAX);
            drawTex(main, 1.0f);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        } else {
            float a = s.mix(dt);
            drawTex(prev, 1.0f - a);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            drawTex(main, a);
        }
        worldCur = 1 - worldCur;

        // ghi ket qua nguoc lai man hinh chinh
        main.bindFramebuffer(true);
        GlStateManager.disableBlend();
        drawTex(worldAccum[worldCur], 1.0f);
        GlStateManager.colorMask(true, true, true, true);
    }

    private void handPass(float dt) {
        BlurSettings s = HandMotionBlur.hand;
        Framebuffer source;

        if (handBlurFrame) {
            Framebuffer prev = handAccum[handCur];
            Framebuffer next = handAccum[1 - handCur];
            next.bindFramebuffer(true);
            GlStateManager.disableBlend();

            if (!handValid) {
                drawTex(handFbo, 1.0f); // frame dau: khong blur, tranh "hien dan"
                handValid = true;
            } else if (s.isMax()) {
                drawTex(prev, s.decay(dt));
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                GL14.glBlendEquation(GL14.GL_MAX);
                drawTex(handFbo, 1.0f);
                GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            } else {
                float a = s.mix(dt);
                drawTex(prev, 1.0f - a);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                drawTex(handFbo, a);
            }
            handCur = 1 - handCur;
            source = handAccum[handCur];
        } else {
            handValid = false;
            source = handFbo; // tay dung yen: ve thang, khong tich luy
        }

        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawTex(source, 1.0f);
    }

    // ------------------------------------------------------------------ logic

    private boolean worldActive() {
        boolean on = HandMotionBlur.master && HandMotionBlur.world.enabled
                && OpenGlHelper.isFramebufferEnabled()
                && mc.theWorld != null
                && !mc.gameSettings.anaglyph
                && !shadersActive();
        if (!on) {
            worldValid = false;
            return false;
        }
        if (mc.theWorld != lastWorld) { // doi the gioi / dimension
            lastWorld = mc.theWorld;
            worldValid = false;
        }
        return true;
    }

    private boolean handActive() {
        if (!HandMotionBlur.master || !HandMotionBlur.hand.enabled) return false;
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
        if (activity) activeUntil = now + 500L + 6L * HandMotionBlur.hand.blurMs;
        return now < activeUntil;
    }

    /** OptiFine shader pack tu quan ly framebuffer -> tat mod de tranh loi. */
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
        if (handFbo == null) {
            handFbo = new Framebuffer(w, h, true);
            handFbo.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
            for (int i = 0; i < 2; i++) {
                handAccum[i] = new Framebuffer(w, h, false);
                worldAccum[i] = new Framebuffer(w, h, false);
                handAccum[i].setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
                worldAccum[i].setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
            }
            handValid = false;
            worldValid = false;
        } else if (handFbo.framebufferWidth != w || handFbo.framebufferHeight != h) {
            handFbo.createBindFramebuffer(w, h);
            for (int i = 0; i < 2; i++) {
                handAccum[i].createBindFramebuffer(w, h);
                worldAccum[i].createBindFramebuffer(w, h);
            }
            handValid = false;
            worldValid = false;
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
        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.colorMask(true, true, true, true);
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
