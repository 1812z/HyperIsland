// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0
package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;


import android.os.Handler;
import android.os.SystemClock;

/**
 * ZN first. Visualizer runs only while ZN is disconnected or delivers no usable frames.
 * During a stall fallback ZN is only probed briefly, so both paths never run continuously;
 * the first healthy probe frame switches back.
 */
final class SelectableSpectrum implements SpectrumCapture {
    private static final long NATIVE_STALL_MS = 1500, STALL_CHECK_MS = 250;
    private static final long PROBE_INTERVAL_MS = 10000, PROBE_WINDOW_MS = 2000;
    private final SpectrumCapture.Reporter reporter;
    private final Handler main;
    private final LiveSpectrum visualizer;
    private final NativeSpectrum nativeSpectrum;
    private final Runnable stallCheck = this::checkStall;
    private final Runnable probeStart = this::startProbe, probeEnd = this::endProbe;
    // Main-looper owned.
    private boolean closed, demanded, nativeAvailable, nativeStalled, probing;
    private int subscribers;
    private long nativeSince, lastNativeFrame;
    private boolean nativeSilenced, visualizerSilenced, sentSilenced;
    private final Sink sink;
    private volatile String mode = "visualizer";

    SelectableSpectrum(SpectrumCapture.Reporter reporter, Handler main, Sink sink) {
        this.reporter = reporter;
        this.main = main;
        this.sink = sink;
        visualizer = new LiveSpectrum(reporter, main, new Sink() {
            @Override public void frame(float[] levels, long time, boolean healthy) {
                if (!nativeActive()) sink.frame(levels, time, healthy);
            }
            @Override public void silenced(boolean value) { visualizerSilenced = value; forwardSilenced(); }
        });
        nativeSpectrum = new NativeSpectrum((message, failure) -> {
            reporter.report(message, failure);
            // Reported on the native I/O thread: move the state change to the main looper.
            if (message.startsWith("native_results_connected ")) main.post(() -> available(true));
            else if (message.startsWith("native_results_disconnected")
                    || message.startsWith("native_results_unavailable")) main.post(() -> available(false));
        }, main, new Sink() {
            @Override public void frame(float[] levels, long time, boolean healthy) {
                if (healthy) nativeFrame();
                if (nativeActive()) sink.frame(levels, time, healthy);
            }
            @Override public void silenced(boolean value) { nativeSilenced = value; forwardSilenced(); }
        });
        applyDemand();
        reporter.report("spectrum_backend policy=native_first fallback=visualizer", null);
    }

    private boolean nativeActive() { return nativeAvailable && !nativeStalled; }

    /** Forward only the active source's stop-at-volume-0 state; re-sent on mode switches. */
    private void forwardSilenced() {
        boolean value = nativeActive() ? nativeSilenced : visualizerSilenced;
        if (closed || value == sentSilenced) return;
        sentSilenced = value;
        sink.silenced(value);
    }

    private void available(boolean value) {
        if (closed || nativeAvailable == value) return;
        nativeAvailable = value;
        // A fresh connection gets a fresh chance to deliver.
        nativeStalled = false; stopProbing();
        nativeSince = SystemClock.uptimeMillis();
        reporter.report("native_backend_available=" + value, null);
        applyDemand();
    }

    private void nativeFrame() {
        if (closed) return;
        lastNativeFrame = SystemClock.uptimeMillis();
        if (nativeStalled) {
            nativeStalled = false; stopProbing();
            reporter.report("native_capture_recovered", null);
            applyDemand();
        }
    }

    private void checkStall() {
        if (closed || !demanded || !nativeActive()) return;
        long now = SystemClock.uptimeMillis();
        // Stop-at-volume-0 pauses ZN on purpose; that is not a failure.
        if (nativeSpectrum.mutedIdle()) lastNativeFrame = now;
        if (now - Math.max(nativeSince, lastNativeFrame) >= NATIVE_STALL_MS) {
            nativeStalled = true;
            reporter.report("native_capture_stalled fallback=visualizer", null);
            applyDemand();
            main.postDelayed(probeStart, PROBE_INTERVAL_MS);
        } else main.postDelayed(stallCheck, STALL_CHECK_MS);
    }

    private void startProbe() {
        if (closed || !nativeStalled || !demanded || !nativeAvailable) return;
        probing = true;
        applyDemand();
        main.postDelayed(probeEnd, PROBE_WINDOW_MS);
    }

    private void endProbe() {
        if (closed || !probing) return;
        probing = false;
        applyDemand();
        if (nativeStalled && demanded) main.postDelayed(probeStart, PROBE_INTERVAL_MS - PROBE_WINDOW_MS);
    }

    private void stopProbing() {
        probing = false;
        main.removeCallbacks(probeStart); main.removeCallbacks(probeEnd);
    }

    private void applyDemand() {
        if (closed) return;
        boolean useNative = nativeActive();
        visualizer.demand(demanded && !useNative, subscribers);
        nativeSpectrum.demand(demanded && nativeAvailable && (!nativeStalled || probing), subscribers);
        main.removeCallbacks(stallCheck);
        if (demanded && useNative) main.postDelayed(stallCheck, STALL_CHECK_MS);
        String next = useNative ? "native" : "visualizer";
        forwardSilenced();
        if (!next.equals(mode)) {
            mode = next;
            reporter.report("spectrum_mode=" + next + " available=" + nativeAvailable + " stalled=" + nativeStalled, null);
        }
    }

    // One fixed worker keeps artwork decoding single-threaded across mode switches.
    @Override public void execute(Runnable action) { visualizer.execute(action); }
    @Override public void audio(android.content.Context context) {
        visualizer.audio(context); nativeSpectrum.audio(context);
    }
    @Override public void media(String packageName, int userId) {
        visualizer.media(packageName, userId); nativeSpectrum.media(packageName, userId);
    }
    @Override public void stopWhenMuted(boolean value) {
        visualizer.stopWhenMuted(value); nativeSpectrum.stopWhenMuted(value);
    }
    @Override public void demand(boolean enabled, int count) {
        if (closed) return;
        if (enabled != demanded) {
            stopProbing();
            if (enabled) {
                nativeSince = SystemClock.uptimeMillis();
                // Resuming playback is a cheap moment to check whether ZN recovered.
                if (nativeStalled) main.post(probeStart);
            }
        }
        demanded = enabled; subscribers = count; applyDemand();
    }
    @Override public String captureMode() { return mode; }
    @Override public void close() {
        closed = true; demanded = false;
        main.removeCallbacks(stallCheck); stopProbing();
        nativeSpectrum.close();
        visualizer.close();
    }
}
