package com.handmotionblur;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Bo cai dat cho mot nhom blur (hand hoac world). */
public class BlurSettings {
    public final String name;
    public final String[] modes;
    public final boolean hasRotation;
    private final boolean defEnabled;
    private final int defMs;
    private final String defMode;

    public boolean enabled;
    public String mode;
    public int blurMs;
    public int frames;
    /** Chi dung cho che do ROTATION. */
    public int shutterMs = 6;
    public int samples = 16;

    private Property pEnabled, pMode, pBlurMs, pFrames, pShutter, pSamples;

    public BlurSettings(String name, boolean defEnabled, int defMs, String defMode, String[] modes, boolean hasRotation) {
        this.name = name;
        this.defEnabled = defEnabled;
        this.defMs = defMs;
        this.defMode = defMode;
        this.modes = modes;
        this.hasRotation = hasRotation;
        this.enabled = defEnabled;
        this.blurMs = defMs;
        this.mode = defMode;
    }

    void load(Configuration cfg) {
        pEnabled = cfg.get(name, "enabled", defEnabled, "Bat/tat nhom '" + name + "'.");
        pMode = cfg.get(name, "mode", defMode,
                hasRotation
                        ? "ROTATION = blur theo huong xoay camera (khong ghosting, dung yen thi khong blur). MIX/MAX = accumulation."
                        : "MIX = tron muot (khuyen dung), MAX = lay gia tri sang nhat (vet ro hon).",
                modes);
        pBlurMs = cfg.get(name, "blurMs", defMs,
                "Chi cho MIX/MAX: do dai vet blur tinh bang ms (5-300). Khong phu thuoc FPS. Bi bo qua neu frames > 0.", 5, 300);
        pFrames = cfg.get(name, "frames", 0,
                "Chi cho MIX/MAX: so frame tron lai (0-32). 0 = dung blurMs. >0 = tron co dinh ~N frame (phu thuoc FPS).", 0, 32);
        enabled = pEnabled.getBoolean();
        mode = pMode.getString();
        blurMs = pBlurMs.getInt();
        frames = pFrames.getInt();
        if (hasRotation) {
            pShutter = cfg.get(name, "shutterMs", 6,
                    "Chi cho ROTATION: thoi gian man trap (ms). Lon hon = vet blur dai hon khi xoay (1-50).", 1, 50);
            pSamples = cfg.get(name, "samples", 16,
                    "Chi cho ROTATION: so mau toi da khi blur (2-48). Cao hon = vet muot hon nhung ton hieu nang.", 2, 48);
            shutterMs = pShutter.getInt();
            samples = pSamples.getInt();
        }
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

    public void setShutterMs(int v) {
        shutterMs = Math.max(1, Math.min(50, v));
        if (pShutter != null) pShutter.set(shutterMs);
        HandMotionBlur.save();
    }

    public void setSamples(int v) {
        samples = Math.max(2, Math.min(48, v));
        if (pSamples != null) pSamples.set(samples);
        HandMotionBlur.save();
    }

    public boolean isMax() {
        return "MAX".equals(mode);
    }

    public boolean isRotation() {
        return hasRotation && "ROTATION".equals(mode);
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
