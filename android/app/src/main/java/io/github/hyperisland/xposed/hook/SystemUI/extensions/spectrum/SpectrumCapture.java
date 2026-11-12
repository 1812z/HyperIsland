// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0
package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import io.github.hyperisland.BuildConfig;

import android.content.Context;
import android.os.Handler;

public interface SpectrumCapture {
    static SpectrumCapture create(SpectrumCapture.Reporter reporter, Handler main, Sink sink) {
        return !BuildConfig.NATIVE_TAP_BUILD.isEmpty()
                ? new SelectableSpectrum(reporter, main, sink) : new LiveSpectrum(reporter, main, sink);
    }
    interface Reporter {
        void report(String message, Throwable failure);
    }

    interface Sink {
        void frame(float[] levels, long uptime, boolean healthy);
        /** Main looper: stop-at-volume-0 intentionally paused sampling, not a failure. */
        default void silenced(boolean value) { }
    }
    void execute(Runnable action);
    void audio(Context context);
    void media(String packageName, int userId);
    void stopWhenMuted(boolean value);
    void demand(boolean enabled, int count);
    default String captureMode() { return "visualizer"; }
    void close();
}
