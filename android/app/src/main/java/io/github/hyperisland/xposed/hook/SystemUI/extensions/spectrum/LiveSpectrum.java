// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.util.Arrays;

/** Shared continuous capture; UI receives only the latest frame, never an unbounded queue. */
final class LiveSpectrum implements SpectrumCapture {
    private final SpectrumCapture.Reporter reporter;
    private final Sink sink;
    private final HandlerThread thread = new HandlerThread("IslandSpectrumLive");
    private final Handler worker;
    private final Handler main;
    private boolean closed;
    private boolean demanded;
    private Visualizer visualizer;
    private long generation;
    private long started, lastFrame, lastLog;
    private long frames;
    private float[] latest = new float[SpectrumDsp.BARS];
    private boolean deliveryQueued;
    private int subscribers;
    private AudioManager audio;
    private PlaybackSource playbackSource;
    private PlaybackSource.State source;
    private int captureSession;
    private int sourceChannels, captureChannels;
    private double channelGain = 1;
    private OutputRoute outputRoute;
    private OutputRoute.State output;
    private final VolumeCompensation compensation = new VolumeCompensation();
    private double outputGain = 1;
    private long lastVolume;
    private int volume, maxVolume;
    private boolean muted;
    private boolean stopWhenMuted = true;
    private boolean silencedSent;
    private long lastVolumeError, captureAfter, retryAfter;
    private final Visualizer.MeasurementPeakRms measurement = new Visualizer.MeasurementPeakRms();
    private boolean measurementReady;
    private boolean normalized;
    private SpectrumDsp dsp;
    private BassResponse bass;
    private final Runnable watchdog = this::watchdog;
    private final Runnable deliver = this::deliver;
    private final Runnable outputPoll = this::pollOutput;

    LiveSpectrum(SpectrumCapture.Reporter reporter, Handler main, Sink sink) {
        this.reporter = reporter; this.main = main; this.sink = sink;
        thread.start(); worker = new Handler(thread.getLooper());
    }

    @Override public void execute(Runnable action) {
        worker.post(() -> {
            synchronized (this) { if (closed) return; }
            action.run();
        });
    }

    @Override public void audio(Context context) {
        execute(() -> {
            audio = context.getSystemService(AudioManager.class);
            outputRoute = audio == null ? null : new OutputRoute(audio);
            playbackSource = audio == null ? null : new PlaybackSource(context, audio);
        });
    }

    @Override public void media(String packageName, int userId) {
        execute(() -> {
            if (playbackSource == null) return;
            try { playbackSource.media(packageName, userId); }
            catch (ReflectiveOperationException | RuntimeException failure) {
                reporter.report("spectrum_media_uid_unavailable", failure);
            }
        });
    }

    @Override public void stopWhenMuted(boolean value) {
        execute(() -> {
            synchronized (this) {
                stopWhenMuted = value;
                if (value && muted) {
                    latest = new float[SpectrumDsp.BARS];
                    queueDelivery();
                }
            }
        });
    }

    private boolean source() {
        PlaybackSource.State next;
        Throwable error = null;
        try {
            next = playbackSource == null ? new PlaybackSource.State(-1, -1, false, "audio_unavailable")
                    : playbackSource.read();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            next = new PlaybackSource.State(-1, -1, false, "query_" + failure.getClass().getSimpleName());
            error = failure;
        }
        boolean changed = !next.equals(source);
        if (changed) reporter.report("spectrum_source sessionId=" + next.sessionId() + " uid=" + next.uid()
                + " spatial=" + next.spatial() + " reason=" + next.reason()
                + " sourceChannels=" + next.sourceChannels() + " captureChannels=" + next.captureChannels(), error);
        source = next;
        return changed;
    }

    private boolean volume(long now, boolean force) {
        if (outputRoute == null || (!force && now - lastVolume < 200)) return false;
        lastVolume = now;
        try {
            OutputRoute.State next = outputRoute.read(source == null ? -1 : source.uid(),
                    visualizer == null ? -1 : captureSession, generation);
            boolean changed = output != null && (!output.key.equals(next.key) || output.mode != next.mode);
            boolean silenceExit = output != null && output.muted && !next.muted;
            boolean log = output == null || changed || output.volume != next.volume
                    || output.muted != next.muted || !output.reason.equals(next.reason)
                    || Float.compare(output.appliedDb, next.appliedDb) != 0;
            output = next;
            volume = next.volume; maxVolume = next.maximum; muted = next.muted;
            double gain = next.gain();
            compensation.target(gain, now, force || changed, muted);
            if (log) reporter.report("spectrum_output type=" + next.type + " behavior=" + next.behavior
                    + " captureVolume=" + next.mode + " reason=" + next.reason
                    + " volume=" + volume + "/" + maxVolume + " muted=" + muted + " silenceExit=" + silenceExit
                    + " volumeDb=" + next.volumeDb + " maximumDb=" + next.maximumDb
                    + " appliedDb=" + next.appliedDb + " policyOutput=" + next.policyOutput
                    + " desiredGain=" + next.gain(), null);
            return changed;
        } catch (RuntimeException failure) {
            // Do not carry inverse software volume through a failed route query.
            compensation.target(VolumeCompensation.DISPLAY_GAIN, now, true);
            if (now - lastVolumeError >= 5000) {
                lastVolumeError = now;
                reporter.report("spectrum_volume_unavailable", failure);
            }
            return false;
        }
    }

    @Override public void demand(boolean enabled, int count) {
        worker.post(() -> {
            synchronized (this) {
                if (closed) return;
                subscribers = count;
                demanded = enabled;
                if (!enabled) {
                    stop("no_visible_playback");
                    retryAfter = 0; // An ordinary stop is not a failure; resume immediately.
                    return;
                }
                if (visualizer == null) {
                    long now = SystemClock.uptimeMillis();
                    volume(now, true);
                    source();
                    // Honour the failure cooldown; pollOutput retries once it expires.
                    if (source.sessionId() >= 0 && now >= retryAfter) start(false);
                }
                worker.removeCallbacks(outputPoll);
                worker.postDelayed(outputPoll, 200);
            }
        });
    }

    private synchronized void pollOutput() {
        if (closed || !demanded) return;
        long now = SystemClock.uptimeMillis();
        boolean changed = volume(now, false);
        boolean sourceChanged = source();
        if (changed || sourceChanged) {
            stop(sourceChanged ? "capture_source_changed" : "output_changed", source.sessionId() < 0);
            retryAfter = 0;
        }
        if (visualizer == null && source.sessionId() >= 0 && now >= retryAfter) start(changed || sourceChanged);
        worker.postDelayed(outputPoll, 200);
    }

    private void start(boolean keepFrame) {
        long session = ++generation;
        try {
            captureSession = source.sessionId();
            sourceChannels = source.sourceChannels(); captureChannels = source.captureChannels();
            visualizer = new Visualizer(captureSession);
            check(visualizer.setEnabled(false));
            int[] range = Visualizer.getCaptureSizeRange();
            int size = 1024;
            while (size < range[0]) size *= 2;
            while (size > range[1]) size /= 2;
            check(visualizer.setCaptureSize(size));
            measurementReady = visualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) == Visualizer.SUCCESS;
            normalized = measurementReady;
            check(visualizer.setScalingMode(normalized ? Visualizer.SCALING_MODE_NORMALIZED : Visualizer.SCALING_MODE_AS_PLAYED));
            dsp = new SpectrumDsp(size); bass = new BassResponse();
            compensation.resetEnvelope();
            check(visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] bytes, int rate) {
                    synchronized (LiveSpectrum.this) {
                        if (closed || session != generation || v != visualizer) return;
                        long now = SystemClock.uptimeMillis();
                        if (now < captureAfter) return;
                        try {
                            if (measurementReady && v.getMeasurementPeakRms(measurement) != Visualizer.SUCCESS) {
                                // Without raw RMS, normalized capture would amplify quiet passages again.
                                measurementReady = normalized = false;
                                check(v.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED));
                                reporter.report("spectrum_measurement_unavailable fallback=as_played", null);
                                return; // The current waveform still belongs to the previous scaling mode.
                            }
                            channelGain = CaptureChannels.gain(sourceChannels, captureChannels, normalized);
                            outputGain = compensation.captureGain(now, normalized, measurement.mRms, channelGain);
                            latest = dsp.levels(bytes, rate, normalized, measurement.mRms, false, outputGain);
                            latest[0] = bass.level(dsp.bandAmplitudes()[0], now);
                            // Keep capturing real PCM so unmute can use the existing smooth onset.
                            // Turning this option off never manufactures signal from silent PCM.
                            boolean silent = stopWhenMuted && muted;
                            if (silent) Arrays.fill(latest, 0);
                            if (silent != silencedSent) {
                                silencedSent = silent;
                                // Posted before this frame's delivery, so views settle first.
                                main.post(() -> {
                                    synchronized (LiveSpectrum.this) { if (closed) return; }
                                    sink.silenced(silent);
                                });
                            }
                        }
                        catch (RuntimeException failure) {
                            reporter.report("live_capture_error reason=invalid_fft", failure);
                            stop("invalid_fft"); return;
                        }
                        lastFrame = now;
                        frames++;
                        queueDelivery();
                        if (lastFrame - lastLog >= 5000) {
                            lastLog = lastFrame;
                            reporter.report("live_capture_tick sessionId=" + captureSession
                                    + " rmsMb=" + measurement.mRms + " levels=" + Arrays.toString(latest), null);
                        }
                    }
                }
                @Override public void onFftDataCapture(Visualizer v, byte[] bytes, int rate) { }
            }, Math.min(20000, Visualizer.getMaxCaptureRate()), true, false));
            check(visualizer.setEnabled(true));
            started = lastLog = SystemClock.uptimeMillis();
            retryAfter = 0;
            captureAfter = started + (keepFrame ? 250 : 100);
            if (!keepFrame) lastFrame = 0;
            frames = 0;
            worker.postDelayed(watchdog, 1000);
            reporter.report("live_capture_start generation=" + generation + " subscribers=" + subscribers
                    + " sessionId=" + captureSession + " spatial=" + source.spatial()
                    + " sourceChannels=" + sourceChannels + " captureChannels=" + captureChannels
                    + " scaling=" + (normalized ? "normalized+raw_rms" : "as_played")
                    + " fft=float captureSize=" + size, null);
        } catch (RuntimeException failure) {
            reporter.report("live_capture_error", failure); stop("setup_failed");
        }
    }

    private static void check(int status) {
        if (status != Visualizer.SUCCESS) throw new IllegalStateException("Visualizer status=" + status);
    }

    private void queueDelivery() {
        if (!deliveryQueued) { deliveryQueued = true; main.post(deliver); }
    }

    private void deliver() {
        float[] levels; long time; boolean healthy;
        synchronized (this) {
            deliveryQueued = false;
            if (closed) return;
            levels = latest; time = lastFrame;
            healthy = visualizer != null && time != 0 && SystemClock.uptimeMillis() - time < 750;
        }
        sink.frame(levels, time, healthy);
    }

    private synchronized void watchdog() {
        if (closed || visualizer == null) return;
        if (SystemClock.uptimeMillis() - Math.max(started, lastFrame) > 1500) {
            reporter.report("live_capture_error reason=no_waveform_callbacks", null);
            stop("stalled");
        } else worker.postDelayed(watchdog, 1000);
    }

    private void stop(String reason) {
        stop(reason, true);
    }

    private void stop(String reason, boolean clearFrame) {
        worker.removeCallbacks(watchdog);
        if (!demanded) worker.removeCallbacks(outputPoll);
        retryAfter = SystemClock.uptimeMillis() + 3000;
        Visualizer old = visualizer; visualizer = null;
        if (clearFrame) { latest = new float[SpectrumDsp.BARS]; lastFrame = 0; }
        if (old != null) {
            try { old.setEnabled(false); } catch (RuntimeException ignored) {}
            try { old.setDataCaptureListener(null, 0, false, false); } catch (RuntimeException ignored) {}
            try { old.release(); }
            catch (RuntimeException failure) { reporter.report("live_capture_error stage=release", failure); }
            reporter.report("live_capture_stop reason=" + reason + " frames=" + frames
                    + " sessionId=" + captureSession, null);
        }
        if (clearFrame) queueDelivery();
    }

    @Override public void close() {
        synchronized (this) {
            closed = true;
            worker.removeCallbacksAndMessages(null);
            stop("hot_reload");
            main.removeCallbacks(deliver);
        }
        thread.quitSafely();
        try { thread.join(1500); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Capture retirement interrupted", e); }
        if (thread.isAlive()) throw new IllegalStateException("Capture worker did not retire");
    }
}
