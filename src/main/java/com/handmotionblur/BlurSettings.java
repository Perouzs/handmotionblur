package com.handmotionblur;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Bo cai dat cho mot nhom blur (hand hoac world). */
public class BlurSettings {
    public final String name;
    private final boolean defEnabled;
    private final int defMs;

    public boolean enabled;
    public String mode = "MIX";
    public int blurMs;
    public int frames;

    private Property pEnabled, pMode, pBlurMs, pFrames;

    public BlurSettings(String name, boolean defEnabled, int defMs) {
        this.name = name;
        this.defEnabled = defEnabled;
        this.defMs = defMs;
        this.enabled = defEnabled;
        this.blurMs = defMs;
    }

    void load(Configuration cfg) {
        pEnabled = cfg.get(name, "enabled", defEnabled, "Bat/tat nhom '" + name + "'.");
        pMode = cfg.get(name, "mode", "MIX",
                "MIX = tron muot (khuyen dung), MAX = lay gia tri sang nhat (vet ro hon).",
                new String[]{"MIX", "MAX"});
        pBlurMs = cfg.get(name, "blurMs", defMs,
                "Do dai vet blur tinh bang ms (5-300). Khong phu thuoc FPS. Bi bo qua neu frames > 0.", 5, 300);
        pFrames = cfg.get(name, "frames", 0,
                "So frame tron lai (0-32). 0 = dung blurMs. >0 = tron co dinh ~N frame (phu thuoc FPS).", 0, 32);
        enabled = pEnabled.getBoolean();
        mode = pMode.getString();
        blurMs = pBlurMs.getInt();
        frames = pFrames.getInt();
    }

    public void setEnabled(boolean v) {
        enabled = v;
        if (pEnabled != null) pEnabled.set(v);
        HandMotionBlur.save();
    }

    public void setMode(String m) {
        mode = m;
        if (pMode != null) pMode.set(m);
        HandMotionBlur.save();
    }

    public void setBlurMs(int v) {
        blurMs = Math.max(5, Math.min(300, v));
        if (pBlurMs != null) pBlurMs.set(blurMs);
        HandMotionBlur.save();
    }

    public void setFrames(int v) {
        frames = Math.max(0, Math.min(32, v));
        if (pFrames != null) pFrames.set(frames);
        HandMotionBlur.save();
    }

    public boolean isMax() {
        return "MAX".equals(mode);
    }

    /** He so tron frame moi vao buffer tich luy (MIX). */
    public float mix(float dt) {
        if (frames > 0) return 1.0f / frames;
        float tau = Math.max(0.005f, blurMs / 1000.0f);
        return 1.0f - (float) Math.exp(-dt / tau);
    }

    /** He so suy giam buffer cu (MAX). */
    public float decay(float dt) {
        if (frames > 0) return 1.0f - 1.0f / frames;
        float tau = Math.max(0.005f, blurMs / 1000.0f);
        return (float) Math.exp(-dt / tau);
    }
}
