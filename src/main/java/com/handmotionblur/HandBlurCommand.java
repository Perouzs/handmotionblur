package com.handmotionblur;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;

/** /handblur (alias /hmb). Chinh config ngay trong game, tu dong luu. */
public class HandBlurCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "handblur";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("hmb");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/handblur status | toggle | <hand|world> <toggle|ms|frames|mode|shutter|samples|clear|thirdperson> [gia tri]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            status(sender);
            return;
        }
        String a0 = args[0].toLowerCase();
        if (a0.equals("toggle")) {
            HandMotionBlur.setMaster(!HandMotionBlur.master);
            msg(sender, "Motion Blur: " + (HandMotionBlur.master ? "ON" : "OFF"));
            return;
        }

        BlurSettings s;
        int off;
        if (a0.equals("hand")) {
            s = HandMotionBlur.hand;
            off = 1;
        } else if (a0.equals("world")) {
            s = HandMotionBlur.world;
            off = 1;
        } else {
            s = HandMotionBlur.hand; // lenh cu: /handblur ms 40 -> ap dung cho hand
            off = 0;
        }
        if (args.length <= off) {
            msg(sender, getCommandUsage(sender));
            return;
        }
        String sub = args[off].toLowerCase();
        String val = args.length > off + 1 ? args[off + 1] : null;

        if (sub.equals("toggle")) {
            s.setEnabled(!s.enabled);
            msg(sender, s.name + ": " + (s.enabled ? "ON" : "OFF"));
        } else if (sub.equals("ms")) {
            Integer v = parseInt(sender, val, 1, 300);
            if (v == null) return;
            s.setBlurMs(v);
            msg(sender, s.name + ".blurMs = " + s.blurMs
                    + (s.frames > 0 ? "  (dang bi bo qua vi frames > 0; dung frames 0)" : ""));
        } else if (sub.equals("frames")) {
            Integer v = parseInt(sender, val, 0, 32);
            if (v == null) return;
            s.setFrames(v);
            msg(sender, s.name + ".frames = " + s.frames + (s.frames == 0 ? "  (dung blurMs)" : "  (tron co dinh ~N frame)"));
        } else if (sub.equals("mode")) {
            String m = null;
            if (val != null) {
                for (String cand : s.modes) {
                    if (cand.equalsIgnoreCase(val)) m = cand;
                }
            }
            if (m == null) {
                msg(sender, "Dung: /handblur " + s.name + " mode <" + String.join("|", s.modes).toLowerCase() + ">");
                return;
            }
            s.setMode(m);
            msg(sender, s.name + ".mode = " + s.mode);
        } else if (sub.equals("shutter") || sub.equals("samples")) {
            if (!s.hasRotation) {
                msg(sender, "Lenh nay chi dung cho 'world' (che do ROTATION).");
                return;
            }
            boolean shut = sub.equals("shutter");
            Integer v = parseInt(sender, val, shut ? 1 : 2, shut ? 50 : 48);
            if (v == null) return;
            if (shut) s.setShutterMs(v); else s.setSamples(v);
            msg(sender, s.name + "." + (shut ? "shutterMs = " + s.shutterMs : "samples = " + s.samples)
                    + (s.isRotation() ? "" : "  (chi co tac dung khi mode = rotation)"));
        } else if (sub.equals("clear")) {
            if (!s.hasRotation) {
                msg(sender, "Lenh nay chi dung cho 'world' (che do ROTATION).");
                return;
            }
            Integer v = parseInt(sender, val, 0, 100);
            if (v == null) return;
            s.setCenterClear(v);
            msg(sender, s.name + ".centerClear = " + s.centerClear + (v == 0 ? "  (blur ca man hinh)" : "  (vung giua giu net)")
                    + (s.isRotation() ? "" : "  (chi co tac dung khi mode = rotation)"));
        } else if (sub.equals("thirdperson")) {
            if (!s.hasRotation) {
                msg(sender, "Lenh nay chi dung cho 'world' (che do ROTATION).");
                return;
            }
            if (val == null || !(val.equalsIgnoreCase("on") || val.equalsIgnoreCase("off"))) {
                msg(sender, "Dung: /handblur world thirdperson <on|off>");
                return;
            }
            s.setThirdPerson(val.equalsIgnoreCase("on"));
            msg(sender, s.name + ".thirdPerson = " + (s.thirdPerson ? "ON" : "OFF"));
        } else {
            msg(sender, getCommandUsage(sender));
        }
    }

    private Integer parseInt(ICommandSender sender, String val, int min, int max) {
        if (val == null) {
            msg(sender, "Thieu gia tri (" + min + "-" + max + ").");
            return null;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(val)));
        } catch (NumberFormatException ex) {
            msg(sender, "Gia tri khong hop le: " + val);
            return null;
        }
    }

    private void status(ICommandSender sender) {
        msg(sender, "Motion Blur: " + (HandMotionBlur.master ? "ON" : "OFF"));
        line(sender, HandMotionBlur.hand);
        line(sender, HandMotionBlur.world);
        msg(sender, getCommandUsage(sender));
    }

    private void line(ICommandSender sender, BlurSettings s) {
        String extra = s.hasRotation ? " | shutterMs=" + s.shutterMs + " | samples=" + s.samples
                + " | centerClear=" + s.centerClear + " | thirdPerson=" + (s.thirdPerson ? "ON" : "OFF") : "";
        msg(sender, s.name + ": " + (s.enabled ? "ON" : "OFF") + " | mode=" + s.mode
                + " | blurMs=" + s.blurMs + " | frames=" + s.frames + extra);
    }

    private void msg(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText("[Blur] " + text));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "toggle", "hand", "world");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("hand") || args[0].equalsIgnoreCase("world"))) {
            return getListOfStringsMatchingLastWord(args, "toggle", "ms", "frames", "mode", "shutter", "samples", "clear", "thirdperson");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("thirdperson")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("mode")) {
            return args[0].equalsIgnoreCase("world")
                    ? getListOfStringsMatchingLastWord(args, "rotation", "mix", "max")
                    : getListOfStringsMatchingLastWord(args, "mix", "max");
        }
        return null;
    }
}
