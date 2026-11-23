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

@Mod(modid = HandMotionBlur.MODID, name = "Motion Blur (Hand + World)", version = "1.3.1",
        acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public class HandMotionBlur {
    public static final String MODID = "handmotionblur";

    /** Cong tac tong (phim V). */
    public static boolean master = true;
    public static boolean always = false;

    /** Blur cho tay / vat pham cam. */
    public static final BlurSettings hand = new BlurSettings("hand", true, 40, "MIX", new String[]{"MIX", "MAX"}, false);
    /** Blur cho toan bo the gioi (camera, mob, nguoi choi khac...). */
    public static final BlurSettings world = new BlurSettings("world", true, 25, "MIX", new String[]{"MIX", "MAX", "ROTATION"}, true);

    private static Configuration config;
    private static Property pMaster, pAlways;
    private static KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        config = new Configuration(e.getSuggestedConfigurationFile());
        config.load();
        pMaster = config.get("general", "enabled", true, "Cong tac tong: bat/tat toan bo mod.");
        pAlways = config.get("debug", "alwaysActive", false,
                "Luon render tay qua FBO (ke ca khi dung yen). Chi de debug.");
        master = pMaster.getBoolean();
        always = pAlways.getBoolean();
        hand.load(config);
        world.load(config);
        config.save();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        toggleKey = new KeyBinding("Toggle Motion Blur", Keyboard.KEY_V, "Motion Blur");
        ClientRegistry.registerKeyBinding(toggleKey);
        MinecraftForge.EVENT_BUS.register(new HandBlurRenderer());
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new HandBlurCommand());
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (toggleKey != null && toggleKey.isPressed()) {
            setMaster(!master);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("Motion Blur: " + (master ? "ON" : "OFF")));
            }
        }
    }

    public static void setMaster(boolean v) {
        master = v;
        if (pMaster != null) pMaster.set(v);
        save();
    }

    public static void save() {
        if (config != null) config.save();
    }
}
