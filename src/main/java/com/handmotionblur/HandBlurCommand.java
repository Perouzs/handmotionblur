package com.handmotionblur;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;

/** /handblur (alias /hmb) - chinh config ngay trong game, tu dong luu. */
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
        return "/handblur <status|toggle|ms <5-300>|frames <0-32>|mode <mix|max>>";
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
        String sub = args[0].toLowerCase();
        if (sub.equals("toggle")) {
            HandMotionBlur.setEnabled(!HandMotionBlur.enabled);
            msg(sender, "Hand Motion Blur: " + (HandMotionBlur.enabled ? "ON" : "OFF"));
        } else if (sub.equals("ms")) {
            Integer v = parseInt(sender, args, 5, 300);
            if (v == null) return;
            HandMotionBlur.setBlurMs(v);
            msg(sender, "blurMs = " + HandMotionBlur.blurMs
                    + (HandMotionBlur.frames > 0 ? "  (dang bi bo qua vi frames > 0; dung /handblur frames 0)" : ""));
        } else if (sub.equals("frames")) {
            Integer v = parseInt(sender, args, 0, 32);
            if (v == null) return;
            HandMotionBlur.setFrames(v);
            msg(sender, "frames = " + HandMotionBlur.frames
                    + (HandMotionBlur.frames == 0 ? "  (dung blurMs)" : "  (tron co dinh ~N frame)"));
        } else if (sub.equals("mode")) {
            if (args.length < 2 || !(args[1].equalsIgnoreCase("mix") || args[1].equalsIgnoreCase("max"))) {
                msg(sender, "Dung: /handblur mode <mix|max>");
                return;
            }
            HandMotionBlur.setMode(args[1].toUpperCase());
            msg(sender, "mode = " + HandMotionBlur.mode);
        } else {
            msg(sender, getCommandUsage(sender));
        }
    }

    private Integer parseInt(ICommandSender sender, String[] args, int min, int max) {
        if (args.length < 2) {
            msg(sender, "Thieu gia tri (" + min + "-" + max + ").");
            return null;
        }
        try {
            int v = Integer.parseInt(args[1]);
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ex) {
            msg(sender, "Gia tri khong hop le: " + args[1]);
            return null;
        }
    }

    private void status(ICommandSender sender) {
        msg(sender, "Hand Motion Blur: " + (HandMotionBlur.enabled ? "ON" : "OFF")
                + " | mode=" + HandMotionBlur.mode
                + " | blurMs=" + HandMotionBlur.blurMs
                + " | frames=" + HandMotionBlur.frames);
        msg(sender, getCommandUsage(sender));
    }

    private void msg(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText("[HandBlur] " + text));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "toggle", "ms", "frames", "mode");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "mix", "max");
        }
        return null;
    }
}
