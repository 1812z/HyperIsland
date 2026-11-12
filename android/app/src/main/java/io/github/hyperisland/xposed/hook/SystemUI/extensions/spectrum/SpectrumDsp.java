// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import java.util.Arrays;

/** Six measured frequency bands with an absolute, fixed response. No per-band AGC. */
public final class SpectrumDsp {
    public static final int BARS = 6;
    private static final double[] EDGES_HZ = {40, 180, 500, 1100, 2400, 4000, 12000};
    // Source-level bass needs headroom after software-volume recovery. These fixed weights
    // retain broad midrange without stretching sustained bass or a weak treble component.
    private static final double[] GAIN = {0.45, 1.15, 2.6, 4.2, 6, 9};
    // A fixed reference preserves song dynamics instead of stretching each quiet frame to full height.
    private static final double REFERENCE_AMPLITUDE = 0.14;
    private final WaveformFft fft;
    private final double[] scratch;
    private final double[] bands = new double[BARS];
    private double restoreGain;

    SpectrumDsp(int captureSize) {
        fft = new WaveformFft(captureSize);
        scratch = new double[captureSize / 2];
    }

    float[] levels(byte[] waveform, int sampleRateMilliHz, boolean normalized, int rmsMb, boolean muted) {
        return levels(waveform, sampleRateMilliHz, normalized, rmsMb, muted, 1);
    }

    float[] levels(byte[] waveform, int sampleRateMilliHz, boolean normalized, int rmsMb,
                   boolean muted, double outputGain) {
        if (sampleRateMilliHz <= 0) throw new IllegalArgumentException("Positive sample rate required");
        fft.analyze(waveform);
        // MeasurementPeakRms is before capture normalization. Undo known software volume
        // before the fixed display response; external headphone volume is absent from RMS.
        // Stereo measurement and delayed capture are different windows: this restores an envelope,
        // not sample-exact original PCM. Preserve song dynamics without frame normalization.
        restoreGain = fft.rms() < 0.001 || muted ? 0 : normalized
                ? (rmsMb <= -9600 ? 0 : Math.pow(10, rmsMb / 2000.0) / fft.rms()) : 1;
        double gain = Double.isFinite(outputGain)
                ? Math.max(0, Math.min(VolumeCompensation.MAX_GAIN, outputGain)) : 1;
        // Bound the combined recovery, not the inverse volume alone. Silence stays zero,
        // and an inaccurate vendor curve cannot turn normalized residue into huge peaks.
        restoreGain *= gain;
        if (normalized) restoreGain = Math.min(4, restoreGain);
        float[] levels = new float[BARS];
        double binHz = sampleRateMilliHz / 1000.0 / waveform.length;
        for (int band = 0; band < BARS; band++) {
            int first = Math.max(1, (int) Math.ceil(EDGES_HZ[band] / binHz));
            int end = Math.min(waveform.length / 2, (int) Math.ceil(EDGES_HZ[band + 1] / binHz));
            int count = Math.max(0, end - first);
            if (count == 0) { bands[band] = 0; continue; }
            for (int bin = first; bin < end; bin++) {
                // Floating FFT avoids a second 8-bit quantization. Reject waveform quantization dust.
                scratch[bin - first] = Math.max(0, fft.amplitude(bin) - 0.00045);
            }
            Arrays.sort(scratch, 0, count);
            int strongest = Math.max(1, (count + 3) / 4);
            double energy = 0;
            for (int i = count - strongest; i < count; i++) energy += scratch[i] * scratch[i];
            double amplitude = 0.55 * scratch[count - 1] + 0.45 * Math.sqrt(energy / strongest);
            bands[band] = amplitude * restoreGain;
            levels[band] = mapAmplitude(band, bands[band]);
        }
        return levels;
    }

    static float mapAmplitude(int band, double measuredAmplitude) {
        double amplitude = measuredAmplitude * GAIN[band] / REFERENCE_AMPLITUDE;
        if (amplitude <= 0) return 0;
        // Nearly linear below the knee: a 20 dB quieter passage really becomes much shorter.
        double value = Math.pow(amplitude, 0.9);
        if (value <= 0.85) return (float) value;
        // A wider knee keeps stronger beats distinct instead of pushing ordinary bass to 95..100%.
        return (float) (0.85 + 0.15 * (1 - Math.exp(-(value - 0.85) / 0.25)));
    }

    double waveformRms() { return fft.rms(); }
    double restoreGain() { return restoreGain; }
    double[] bandAmplitudes() { return bands; }

    /** Fixed response for source amplitudes delivered by the native backend. */
    static float nativeResponse(int band, double sum) {
        if (!Double.isFinite(sum) || sum <= 0) return 0;
        double x = Math.pow(0.6 * sum, 0.4000000059604645) * (1 - 0.05 * band);
        double y = x * x * (3.840823 + x * (-8.182433 + x * (7.772333 - 2.430722 * x)));
        return (float) Math.max(0, Math.min(1, y));
    }

    public static float smooth(float current, float target, float elapsedMs) {
        float timeConstant = target > current ? 28 : 110;
        float value = current + (target - current)
                * (1 - (float) Math.exp(-Math.max(0, Math.min(100, elapsedMs)) / timeConstant));
        return Math.abs(value - target) < 0.001f ? target : value;
    }
}
