// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

/** A bounded accent for measured bass attacks, above the fixed sustained-bass response. */
final class BassResponse {
    private static final double MIN_ATTACK = 0.05, MIN_RISE = 0.018, RISE_RATIO = 1.45;
    private static final double ATTACK_GAIN = 3, MAX_ACCENT = 0.60;
    private static final long HOLD_MS = 80, REFRACTORY_MS = 150, GAP_MS = 250;
    private static final double BASELINE_MS = 120, RELEASE_MS = 55;
    private boolean initialized;
    private double baseline, peak;
    private long last, holdUntil, onsetAt, onsets;

    float level(double amplitude, long now) {
        amplitude = Double.isFinite(amplitude) ? Math.max(0, amplitude) : 0;
        float sustained = SpectrumDsp.mapAmplitude(0, amplitude);
        if (!initialized || now < last || now - last > GAP_MS) {
            initialized = true; baseline = amplitude; peak = 0;
            last = holdUntil = now; onsetAt = now - REFRACTORY_MS;
            return sustained;
        }
        if (amplitude <= 0.0005) {
            baseline = peak = 0; last = holdUntil = now;
            onsetAt = now - REFRACTORY_MS;
            return sustained;
        }
        if (now > holdUntil) peak *= Math.exp(-(now - Math.max(last, holdUntil)) / RELEASE_MS);
        double rise = amplitude - baseline;
        boolean attack = amplitude >= MIN_ATTACK && rise >= MIN_RISE
                && amplitude >= baseline * RISE_RATIO;
        double candidate = Math.min(MAX_ACCENT, amplitude + ATTACK_GAIN * Math.max(0, rise));
        boolean newOnset = now - onsetAt >= REFRACTORY_MS;
        // A later sample in the same attack can refine its peak, without counting another beat.
        if (attack && (newOnset || (now - onsetAt <= HOLD_MS && candidate > peak))) {
            peak = Math.max(peak, candidate); holdUntil = now + HOLD_MS;
            if (newOnset) { onsetAt = now; onsets++; }
        }
        baseline += (amplitude - baseline) * (1 - Math.exp(-(now - last) / BASELINE_MS));
        last = now;
        return Math.max(sustained, SpectrumDsp.mapAmplitude(0, peak));
    }

    long onsets() { return onsets; }
    double peak() { return peak; }
}
