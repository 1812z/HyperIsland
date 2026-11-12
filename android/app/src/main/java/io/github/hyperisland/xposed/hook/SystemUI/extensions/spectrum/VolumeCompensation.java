// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

/** Recover source dynamics independently of the playback-volume setting. No per-band AGC. */
final class VolumeCompensation {
    enum CaptureVolume { SOFTWARE, EXTERNAL, UNKNOWN }
    // One fixed display reference for both speaker and headphones, including quiet passages.
    static final double DISPLAY_GAIN = 0.35;
    static final double MAX_GAIN = 10000;
    private static final long RMS_SETTLE_MS = 500;
    private double current = 1, target = 1;
    private long updated;
    private long settleUntil;
    private boolean initialized, muted;
    private double envelopeRms = Double.NaN;

    static double gain(CaptureVolume mode, float volumeDb, float maximumDb) {
        // External volume is absent from the capture. Never add the headphone attenuation.
        if (mode != CaptureVolume.SOFTWARE) return DISPLAY_GAIN;
        if (!Float.isFinite(maximumDb) || !Float.isFinite(volumeDb)
                || volumeDb > maximumDb) return DISPLAY_GAIN;
        // Undo only known software attenuation. A muted PCM stream cannot be recovered.
        return Math.min(MAX_GAIN, DISPLAY_GAIN * Math.pow(10, (maximumDb - volumeDb) / 20.0));
    }

    static double appliedGain(CaptureVolume mode, float volumeDb, float maximumDb, float appliedDb) {
        // Policy metadata replaces the nominal curve. It must never be multiplied by its inverse
        // again. Absolute-volume routes can still have fixed phone-side attenuation (e.g. CE).
        if (mode != CaptureVolume.UNKNOWN && Float.isFinite(appliedDb)) {
            return gain(CaptureVolume.SOFTWARE, appliedDb, maximumDb);
        }
        return gain(mode, volumeDb, maximumDb);
    }

    void target(double gain, long now, boolean reset) {
        target(gain, now, reset, false);
    }

    void target(double gain, long now, boolean reset, boolean muted) {
        // A silent measurement (or the first unmute buffer before the volume poll)
        // is not a source envelope to preserve. Start unmute with the current curve.
        reset |= initialized && this.muted && !muted;
        this.muted = muted;
        gain = Double.isFinite(gain) ? Math.max(0, Math.min(MAX_GAIN, gain)) : DISPLAY_GAIN;
        // RMS averages old and new mixer buffers. Do not multiply that mixed window
        // by the new inverse volume: lowering the volume would create a false peak.
        if (reset) { settleUntil = 0; resetEnvelope(); }
        else if (initialized && gain != target) settleUntil = now + RMS_SETTLE_MS;
        value(now);
        target = gain;
        if (!initialized || reset) { current = target; initialized = true; }
        updated = now;
    }

    boolean settling(long now) { return now < settleUntil; }

    void resetEnvelope() { envelopeRms = Double.NaN; }

    double captureGain(long now, boolean normalized, int rmsMb, double channelGain) {
        double gain = value(now) * channelGain;
        if (!normalized) { resetEnvelope(); return gain; }
        double rms = rmsMb <= -9600 ? 0 : Math.pow(10, rmsMb / 2000.0);
        if (!settling(now)) envelopeRms = rms * gain;
        else if (rms > 0 && envelopeRms > 0) {
            // Mixed old/new RMS cannot identify source loudness during a volume change.
            // Hold only the last measured common envelope, never six frequency levels.
            // The DSP still analyzes each fresh waveform; actual silence stays silent.
            return envelopeRms / rms;
        }
        return gain;
    }

    double value(long now) {
        if (!initialized) return current;
        long elapsed = Math.max(0, now - updated);
        current += (target - current) * (1 - Math.exp(-elapsed / 80.0));
        updated = now;
        if (Math.abs(current - target) < 0.000001) current = target;
        return current;
    }
}
