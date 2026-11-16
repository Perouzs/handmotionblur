package com.handmotionblur;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
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

    // ---- config ----
    public static boolean enabled = true;
    /** MIX = lerp(accum, hand, a); MAX = max(accum * decay, hand) */
    public static String mode = "MIX";
    /** Do dai vet blur (hang so thoi gian, ms). */
    public static int blurMs = 40;
    /** true = luon dung duong FBO (ke ca khi tay dung yen). Chi de debug. */
    public static boolean always = false;

    private static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        Configuration cfg = new Configuration(e.getSuggestedConfigurationFile());
        cfg.load();
        enabled = cfg.getBoolean("enabled", "general", true, "Bat/tat mod.");
        mode = cfg.getString("mode", "general", "MIX",
                "MIX = tron muot (khuyen dung), MAX = lay gia tri sang nhat (vet ro hon).",
                new String[]{"MIX", "MAX"});
        blurMs = cfg.getInt("blurMs", "general", 40, 5, 300,
                "Do dai vet blur tinh bang ms. Lon hon = vet dai hon. Khong phu thuoc FPS.");
        always = cfg.getBoolean("alwaysActive", "debug", false,
                "Luon render tay qua FBO (ke ca khi dung yen). Chi de debug.");
        if (cfg.hasChanged()) cfg.save();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        toggleKey = new KeyBinding("Toggle Hand Motion Blur", Keyboard.KEY_V, "Hand Motion Blur");
        ClientRegistry.registerKeyBinding(toggleKey);
        MinecraftForge.EVENT_BUS.register(new HandBlurRenderer());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (toggleKey != null && toggleKey.isPressed()) {
            enabled = !enabled;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("Hand Motion Blur: " + (enabled ? "ON" : "OFF")));
            }
        }
    }
}
