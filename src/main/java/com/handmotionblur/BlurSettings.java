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
    /** Ban kinh vung giua man hinh giu net (0-100, % cua nua chieu cao). 0 = blur ca man hinh. */
    public int centerClear = 50;
    /** Co ap dung rotation blur o goc nhin thu ba hay khong. */
    public boolean thirdPerson = true;

    private Property pEnabled, pMode, pBlurMs, pFrames, pShutter, pSamples, pClear, pThird;

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
                "Chi cho MIX/MAX: do dai vet blur tinh bang ms (1-300). Khong phu thuoc FPS. Bi bo qua neu frames > 0.", 1, 300);
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
            pClear = cfg.get(name, "centerClear", 50,
                    "Chi cho ROTATION: vung giua man hinh giu net (0-100, % cua nua chieu cao). 0 = blur ca man hinh. Giup model cua ban / muc tieu o tam khong bi nhoe.", 0, 100);
            pThird = cfg.get(name, "thirdPerson", true,
                    "Chi cho ROTATION: co ap dung blur o goc nhin thu ba (F5) hay khong.");
            shutterMs = pShutter.getInt();
            samples = pSamples.getInt();
            centerClear = pClear.getInt();
            thirdPerson = pThird.getBoolean();
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
        blurMs = Math.max(1, Math.min(300, v));
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

    public void setCenterClear(int v) {
        centerClear = Math.max(0, Math.min(100, v));
        if (pClear != null) pClear.set(centerClear);
        HandMotionBlur.save();
    }

    public void setThirdPerson(boolean v) {
        thirdPerson = v;
        if (pThird != null) pThird.set(v);
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
        float tau = Math.max(0.001f, blurMs / 1000.0f);
        return 1.0f - (float) Math.exp(-dt / tau);
    }

    /** He so suy giam buffer cu (MAX). */
    public float decay(float dt) {
        if (frames > 0) return 1.0f - 1.0f / frames;
        float tau = Math.max(0.001f, blurMs / 1000.0f);
        return (float) Math.exp(-dt / tau);
    }
}
