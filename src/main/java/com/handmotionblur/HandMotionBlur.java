package com.handmotionblur;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = HandMotionBlur.MODID, name = "Hand Motion Blur", version = "1.0.0",
        acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public class HandMotionBlur {
    public static final String MODID = "handmotionblur";

    // ---- gia tri dang dung (doc boi renderer) ----
    public static boolean enabled = true;
    /** MIX = lerp(accum, hand, a); MAX = max(accum * decay, hand) */
    public static String mode = "MIX";
    /** Do dai vet blur (hang so thoi gian, ms). Dung khi frames == 0. */
    public static int blurMs = 40;
    /** 0 = dung blurMs (doc lap FPS). >0 = tron khoang N frame (phu thuoc FPS). */
    public static int frames = 0;
    public static boolean always = false;

    private static Configuration config;
    private static Property pEnabled, pMode, pBlurMs, pFrames, pAlways;
    private static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        config = new Configuration(e.getSuggestedConfigurationFile());
        config.load();
        pEnabled = config.get("general", "enabled", true, "Bat/tat mod.");
        pMode = config.get("general", "mode", "MIX",
                "MIX = tron muot (khuyen dung), MAX = lay gia tri sang nhat (vet ro hon).",
                new String[]{"MIX", "MAX"});
        pBlurMs = config.get("general", "blurMs", 40,
                "Do dai vet blur tinh bang ms (5-300). Khong phu thuoc FPS. Bi bo qua neu frames > 0.", 5, 300);
        pFrames = config.get("general", "frames", 0,
                "So frame tron lai (0-32). 0 = dung blurMs. >0 = tron co dinh ~N frame (vet ngan lai khi FPS cao).", 0, 32);
        pAlways = config.get("debug", "alwaysActive", false,
                "Luon render tay qua FBO (ke ca khi dung yen). Chi de debug.");
        enabled = pEnabled.getBoolean();
        mode = pMode.getString();
        blurMs = pBlurMs.getInt();
        frames = pFrames.getInt();
        always = pAlways.getBoolean();
        config.save();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        toggleKey = new KeyBinding("Toggle Hand Motion Blur", Keyboard.KEY_V, "Hand Motion Blur");
        ClientRegistry.registerKeyBinding(toggleKey);
        MinecraftForge.EVENT_BUS.register(new HandBlurRenderer());
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new HandBlurCommand());
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (toggleKey != null && toggleKey.isPressed()) {
            setEnabled(!enabled);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("Hand Motion Blur: " + (enabled ? "ON" : "OFF")));
            }
        }
    }

    // ---- setters (lenh chat dung) : cap nhat gia tri + luu config ----

    public static void setEnabled(boolean v) {
        enabled = v;
        if (pEnabled != null) pEnabled.set(v);
        save();
    }

    public static void setMode(String m) {
        mode = m;
        if (pMode != null) pMode.set(m);
        save();
    }

    public static void setBlurMs(int v) {
        blurMs = Math.max(5, Math.min(300, v));
        if (pBlurMs != null) pBlurMs.set(blurMs);
        save();
    }

    public static void setFrames(int v) {
        frames = Math.max(0, Math.min(32, v));
        if (pFrames != null) pFrames.set(frames);
        save();
    }

    private static void save() {
        if (config != null) config.save();
    }
}
