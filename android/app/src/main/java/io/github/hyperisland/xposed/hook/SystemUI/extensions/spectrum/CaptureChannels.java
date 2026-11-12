// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

/** Undo only known channel padding, independently of playback volume and frequency bands. */
final class CaptureChannels {
    static double gain(int sourceChannels, int captureChannels, boolean normalized) {
        // Mono can be duplicated into several mixer channels, rather than padded with zeros.
        // Unknown formats and downmixes likewise do not justify an inverse channel coefficient.
        if (sourceChannels < 2 || captureChannels <= sourceChannels || captureChannels > 24) return 1;
        double ratio = (double) captureChannels / sourceChannels;
        // Raw RMS averages all interleaved channels; AS_PLAYED averages the summed waveform.
        return normalized ? Math.sqrt(ratio) : ratio;
    }
}
