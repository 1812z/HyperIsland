// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import java.util.Arrays;

/** Reusable Hann-windowed radix-2 FFT of Visualizer's unsigned 8-bit waveform. */
final class WaveformFft {
    private final int size;
    private final double[] real, imaginary, window, cosine, sine;
    private final int[] reversed;
    private final double amplitudeScale;
    private double rms;

    WaveformFft(int size) {
        if (size < 4 || size > 32768 || Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("Power-of-two capture size required");
        }
        this.size = size;
        real = new double[size]; imaginary = new double[size]; window = new double[size];
        cosine = new double[size / 2]; sine = new double[size / 2]; reversed = new int[size];
        double windowSum = 0;
        int shift = 32 - Integer.numberOfTrailingZeros(size);
        for (int i = 0; i < size; i++) {
            reversed[i] = Integer.reverse(i) >>> shift;
            windowSum += window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / size);
        }
        amplitudeScale = 2 / windowSum;
        for (int i = 0; i < size / 2; i++) {
            cosine[i] = Math.cos(-2 * Math.PI * i / size);
            sine[i] = Math.sin(-2 * Math.PI * i / size);
        }
    }

    void analyze(byte[] waveform) {
        if (waveform == null || waveform.length != size) {
            throw new IllegalArgumentException("Waveform must match capture size");
        }
        double mean = 0;
        for (byte sample : waveform) mean += ((sample & 255) - 128) / 128.0;
        mean /= size;
        double square = 0;
        Arrays.fill(imaginary, 0);
        for (int i = 0; i < size; i++) {
            double sample = ((waveform[i] & 255) - 128) / 128.0 - mean;
            square += sample * sample;
            real[reversed[i]] = sample * window[i];
        }
        rms = Math.sqrt(square / size);
        for (int length = 2; length <= size; length *= 2) {
            int half = length / 2, step = size / length;
            for (int start = 0; start < size; start += length) {
                for (int j = 0; j < half; j++) {
                    int a = start + j, b = a + half, twiddle = j * step;
                    double re = real[b] * cosine[twiddle] - imaginary[b] * sine[twiddle];
                    double im = real[b] * sine[twiddle] + imaginary[b] * cosine[twiddle];
                    real[b] = real[a] - re; imaginary[b] = imaginary[a] - im;
                    real[a] += re; imaginary[a] += im;
                }
            }
        }
    }

    double rms() { return rms; }

    double amplitude(int bin) {
        if (bin <= 0 || bin >= size / 2) return 0;
        return Math.hypot(real[bin], imaginary[bin]) * amplitudeScale;
    }
}
