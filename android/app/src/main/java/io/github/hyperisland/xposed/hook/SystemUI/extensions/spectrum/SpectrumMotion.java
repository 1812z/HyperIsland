// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

/** Persistent visual state: releasing audio capture does not discard the last bar heights. */
class SpectrumMotion {
    static final float PAUSE_MS = 420;
    static final float SILENCE_MS = 120, RECOVERY_MS = 180;
    final float[] heights = new float[SpectrumDsp.BARS];
    private final float[] target = new float[SpectrumDsp.BARS];
    private final float[] pauseFrom = new float[SpectrumDsp.BARS];
    private final float[] recoveryFrom = new float[SpectrumDsp.BARS];
    private boolean playing;
    private boolean quiet;
    private float pauseElapsed = PAUSE_MS;
    private long quietSince;
    private float recoveryElapsed = RECOVERY_MS;

    boolean isPlaying() { return playing; }
    boolean recovering() { return playing && !quiet && recoveryElapsed < RECOVERY_MS; }

    boolean playing(boolean value) {
        if (playing == value) return false;
        playing = value;
        quiet = false; quietSince = 0; recoveryElapsed = RECOVERY_MS;
        java.util.Arrays.fill(target, 0);
        if (!value) {
            System.arraycopy(heights, 0, pauseFrom, 0, heights.length);
            pauseElapsed = 0;
        }
        return true;
    }

    void frame(float[] levels, long uptime) {
        if (!playing) return;
        boolean silent = true;
        for (float level : levels) silent &= level <= 0.001f;
        if (silent) {
            if (!quiet) quietSince = uptime;
            recoveryElapsed = RECOVERY_MS; // Returning to silence must decay immediately.
        } else {
            if (quiet && uptime - quietSince >= SILENCE_MS) {
                System.arraycopy(heights, 0, recoveryFrom, 0, heights.length);
                recoveryElapsed = 0;
            }
        }
        quiet = silent;
        System.arraycopy(levels, 0, target, 0, target.length);
    }

    boolean advance(float elapsedMs) {
        if (!playing) {
            pauseElapsed = Math.min(PAUSE_MS, pauseElapsed + Math.max(0, elapsedMs));
            float remaining = 1 - pauseElapsed / PAUSE_MS;
            float scale = remaining * remaining * remaining;
            for (int i = 0; i < heights.length; i++) heights[i] = pauseFrom[i] * scale;
            return pauseElapsed < PAUSE_MS;
        }
        if (!quiet) recoveryElapsed = Math.min(RECOVERY_MS, recoveryElapsed + Math.max(0, elapsedMs));
        float t = recoveryElapsed / RECOVERY_MS;
        float entry = t * t * (3 - 2 * t);
        boolean moving = recovering();
        for (int i = 0; i < heights.length; i++) {
            // Only the entry after sustained measured silence is eased. Every new
            // frequency target remains live, and ordinary beats keep their fast attack.
            float wanted = recoveryFrom[i] + (target[i] - recoveryFrom[i]) * entry;
            heights[i] = SpectrumDsp.smooth(heights[i], wanted, elapsedMs);
            moving |= Math.abs(heights[i] - target[i]) > 0.001f;
        }
        return moving;
    }

    void hidden() {
        // A hidden pause needs no off-screen animation. Playing state survives a holder handoff.
        if (!playing) { pauseElapsed = PAUSE_MS; java.util.Arrays.fill(heights, 0); }
    }
}
