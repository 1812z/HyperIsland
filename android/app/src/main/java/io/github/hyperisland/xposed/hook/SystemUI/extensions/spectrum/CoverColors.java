// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

/** Deterministic spatial cover colors; every source pixel contributes to the full field. */
final class CoverColors {
    static final int SIZE = 12;
    static final int COUNT = SIZE * SIZE;
    static final int SAMPLE_SIZE = 4;
    static final int TOP_DEFAULT = 0xeedddddd;
    static final int BOTTOM_DEFAULT = 0xcc999999;
    private static final double GRAY_OVERLAY = 0.32;
    private static final double NEUTRAL_GRAY = 153;

    static int[] fallback() {
        return gradient(TOP_DEFAULT, BOTTOM_DEFAULT);
    }

    static int[] gradient(int top, int bottom) {
        int[] colors = new int[COUNT];
        for (int y = 0; y < SIZE; y++) {
            int color = blend(top, bottom, y / (float) (SIZE - 1));
            java.util.Arrays.fill(colors, y * SIZE, (y + 1) * SIZE, color);
        }
        return colors;
    }

    static int[] field(int[] pixels, int width, int height) {
        if (width < SAMPLE_SIZE || height < SAMPLE_SIZE || (long) width * height != pixels.length) {
            throw new IllegalArgumentException("Invalid cover dimensions");
        }
        int samples = SAMPLE_SIZE * SAMPLE_SIZE;
        double[] red = new double[samples], green = new double[samples], blue = new double[samples];
        double[] weight = new double[samples];
        // Area bins cover the entire artwork, including its edges. Alpha is premultiplied
        // before filtering so invisible RGB never contaminates neighboring visible colors.
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pixel = pixels[y * width + x];
            double alpha = (pixel >>> 24) / 255.0;
            int cell = (y * SAMPLE_SIZE / height) * SAMPLE_SIZE + x * SAMPLE_SIZE / width;
            weight[cell] += alpha;
            red[cell] += ((pixel >>> 16) & 255) * alpha;
            green[cell] += ((pixel >>> 8) & 255) * alpha;
            blue[cell] += (pixel & 255) * alpha;
        }
        // Filter broad 4x4 regions before interpolation. Fine text, texture, and small
        // color spots merge into their surrounding cover region instead of reaching bars.
        double[] softRed = new double[samples], softGreen = new double[samples];
        double[] softBlue = new double[samples], softWeight = new double[samples];
        for (int y = 0; y < SAMPLE_SIZE; y++) for (int x = 0; x < SAMPLE_SIZE; x++) {
            double r = 0, g = 0, b = 0, total = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int sx = Math.max(0, Math.min(SAMPLE_SIZE - 1, x + dx));
                int sy = Math.max(0, Math.min(SAMPLE_SIZE - 1, y + dy));
                int cell = sy * SAMPLE_SIZE + sx, kernel = (dx == 0 ? 2 : 1) * (dy == 0 ? 2 : 1);
                r += red[cell] * kernel; g += green[cell] * kernel; b += blue[cell] * kernel;
                total += weight[cell] * kernel;
            }
            int cell = y * SAMPLE_SIZE + x;
            softRed[cell] = r; softGreen[cell] = g; softBlue[cell] = b; softWeight[cell] = total;
        }
        int[] colors = fallback();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            double sx = Math.max(0, Math.min(SAMPLE_SIZE - 1, (x + 0.5) * SAMPLE_SIZE / SIZE - 0.5));
            double sy = Math.max(0, Math.min(SAMPLE_SIZE - 1, (y + 0.5) * SAMPLE_SIZE / SIZE - 0.5));
            double total = sample(softWeight, sx, sy);
            if (total > 0) colors[y * SIZE + x] = visible(sample(softRed, sx, sy) / total,
                    sample(softGreen, sx, sy) / total, sample(softBlue, sx, sy) / total);
        }
        return colors;
    }

    private static double sample(double[] values, double x, double y) {
        int left = (int) x, top = (int) y;
        int right = Math.min(SAMPLE_SIZE - 1, left + 1), bottom = Math.min(SAMPLE_SIZE - 1, top + 1);
        double upper = values[top * SAMPLE_SIZE + left] * (1 - (x - left))
                + values[top * SAMPLE_SIZE + right] * (x - left);
        double lower = values[bottom * SAMPLE_SIZE + left] * (1 - (x - left))
                + values[bottom * SAMPLE_SIZE + right] * (x - left);
        return upper * (1 - (y - top)) + lower * (y - top);
    }

    private static int visible(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b));
        // Lift dark regions enough to remain legible on the island's black background.
        // Preserve the cover's hue without boosting saturation. A neutral gray veil
        // softens both chroma and contrast, while achromatic covers remain neutral.
        double value = Math.max(0.54, Math.min(0.90, max / 255.0)) * 255;
        double scale = max == 0 ? 0 : value / max;
        if (max == 0) r = g = b = value;
        else { r *= scale; g *= scale; b *= scale; }
        double gray = NEUTRAL_GRAY * GRAY_OVERLAY, retained = 1 - GRAY_OVERLAY;
        int rr = (int) Math.round(r * retained + gray);
        int gg = (int) Math.round(g * retained + gray);
        int bb = (int) Math.round(b * retained + gray);
        return 0xee000000 | rr << 16 | gg << 8 | bb;
    }

    static int[] hyperOsField(int[] pixels) {
        int[] pair = extract(pixels);
        return gradient(pair[0], pair[1]);
    }

    private static int[] extract(int[] pixels) {
        double[] weights = new double[4096], red = new double[4096];
        double[] green = new double[4096], blue = new double[4096];
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 128) continue;
            int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            float saturation = max == 0 ? 0 : (max - min) / (float) max;
            if (max < 30 || saturation < 0.15f) continue;
            int bucket = (r >> 4) << 8 | (g >> 4) << 4 | b >> 4;
            double weight = (0.4 + saturation) * ((pixel >>> 24) / 255.0);
            weights[bucket] += weight;
            red[bucket] += r * weight; green[bucket] += g * weight; blue[bucket] += b * weight;
        }
        int best = 0;
        for (int i = 1; i < weights.length; i++) if (weights[i] > weights[best]) best = i;
        if (weights[best] == 0) return new int[]{TOP_DEFAULT, BOTTOM_DEFAULT};
        float r = (float) (red[best] / weights[best] / 255);
        float g = (float) (green[best] / weights[best] / 255);
        float b = (float) (blue[best] / weights[best] / 255);
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = max == r ? (g - b) / delta : max == g ? 2 + (b - r) / delta : 4 + (r - g) / delta;
        if (hue < 0) hue += 6;
        float saturation = Math.min(0.8f, Math.max(0.45f, delta / max));
        return new int[]{hsv(0xee, hue, saturation * 0.85f, 0.94f), hsv(0xcc, hue, saturation, 0.68f)};
    }

    private static int hsv(int alpha, float hue, float saturation, float value) {
        int sector = (int) hue;
        float f = hue - sector, p = value * (1 - saturation);
        float q = value * (1 - saturation * f), t = value * (1 - saturation * (1 - f));
        float[] values = switch (sector) {
            case 0 -> new float[]{value, t, p}; case 1 -> new float[]{q, value, p};
            case 2 -> new float[]{p, value, t}; case 3 -> new float[]{p, q, value};
            case 4 -> new float[]{t, p, value}; default -> new float[]{value, p, q};
        };
        return alpha << 24 | Math.round(values[0] * 255) << 16
                | Math.round(values[1] * 255) << 8 | Math.round(values[2] * 255);
    }

    private static int blend(int from, int to, float fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            color |= Math.round(a + (b - a) * fraction) << shift;
        }
        return color;
    }
}
