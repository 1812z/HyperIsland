// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0
package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import io.github.hyperisland.BuildConfig;

import android.content.Context;
import android.media.AudioManager;
import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.ErrnoException;
import android.system.StructPollfd;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A shared, lifecycle-gated native result consumer. All drawing stays on the main Looper. */
final class NativeSpectrum implements SpectrumCapture {
    private record Control(int uid, int session, boolean ready, long generation) { }
    // Without demand only the companion lease heartbeat (2 s expiry) needs servicing.
    private static final long ACTIVE_MS = 200, IDLE_MS = 1000;
    private final SpectrumCapture.Reporter reporter;
    private final Sink sink;
    private final Handler main;
    private final HandlerThread thread = new HandlerThread("IslandSpectrumNativeControl");
    private final Handler worker;
    private final Thread io;
    private final AtomicLong revision = new AtomicLong(Math.max(1, SystemClock.elapsedRealtimeNanos()));
    private volatile boolean closed, requested;
    private volatile Control control = new Control(-1, -1, false, 1);
    private volatile LocalServerSocket server;
    private volatile LocalSocket client;
    private volatile String mediaPackage;
    private volatile int mediaUser = -1;
    private volatile boolean mutedIdle;
    // Wakes the idle result loop so a new demand is sent at once, not at the next idle tick.
    private volatile FileDescriptor wakeWrite;
    private final AtomicBoolean wakePending = new AtomicBoolean();
    private AudioManager audio;
    private PlaybackSource source;
    private boolean stopWhenMuted = true;
    private long lastError;
    private float[] latest = new float[6];
    private long latestTime;
    private long latestGeneration;
    private boolean deliveryQueued, latestHealthy;
    private final Runnable deliver = this::deliver;
    private final Runnable poll = this::poll;

    NativeSpectrum(SpectrumCapture.Reporter reporter, Handler main, Sink sink) {
        this.reporter = reporter; this.main = main; this.sink = sink;
        thread.start(); worker = new Handler(thread.getLooper());
        io = new Thread(this::serve, "IslandSpectrumNativeResults"); io.start();
        worker.post(poll);
    }
    @Override public void execute(Runnable action) { worker.post(() -> { if (!closed) action.run(); }); }
    @Override public void audio(Context context) {
        execute(() -> { audio = context.getSystemService(AudioManager.class); source = audio == null ? null : new PlaybackSource(context, audio); });
    }
    @Override public void media(String packageName, int userId) {
        if (!java.util.Objects.equals(mediaPackage, packageName) || mediaUser != userId) {
            mediaPackage = packageName; mediaUser = userId; revision.incrementAndGet();
        }
        execute(() -> {
            if (source == null) return;
            try { source.media(packageName, userId); refresh(); }
            catch (ReflectiveOperationException | RuntimeException e) { reporter.report("native_media_unavailable", e); }
        });
    }
    @Override public void stopWhenMuted(boolean value) { execute(() -> { stopWhenMuted = value; refresh(); }); }
    @Override public void demand(boolean enabled, int count) {
        if (requested != enabled) {
            requested = enabled; revision.incrementAndGet();
            // Restart the refresh cadence for the new demand state.
            worker.removeCallbacks(poll); worker.post(poll);
            wake();
        } else execute(this::refresh);
    }
    private void wake() {
        FileDescriptor fd = wakeWrite;
        // At most one unread byte, so this write can never block.
        if (fd != null && wakePending.compareAndSet(false, true)) {
            try { Os.write(fd, new byte[1], 0, 1); } catch (ErrnoException | java.io.InterruptedIOException ignored) { }
        }
    }
    /** True while ZN is paused on purpose by stop-at-volume-0, not because it failed. */
    boolean mutedIdle() { return mutedIdle; }
    private void poll() { if (!closed) { refresh(); worker.postDelayed(poll, requested ? ACTIVE_MS : IDLE_MS); } }
    private void refresh() {
        int uid = -1, session = -1; boolean active = false, muted = false;
        try {
            if (requested && source != null) {
                PlaybackSource.State state = source.readNative(); uid = state.uid(); session = state.sessionId();
                muted = stopWhenMuted && audio.isStreamMute(AudioManager.STREAM_MUSIC);
                active = uid >= 10000 && session > 0 && !muted;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            long now = SystemClock.uptimeMillis();
            if (now - lastError > 5000) { lastError = now; reporter.report("native_source_unavailable", e); }
        }
        if (muted != mutedIdle) {
            mutedIdle = muted;
            final boolean silent = muted;
            main.post(() -> { if (!closed) sink.silenced(silent); });
        }
        Control old = control;
        if (old.uid != uid || old.session != session || old.ready != active) revision.incrementAndGet();
        long generation = revision.get();
        control = new Control(uid, session, active, generation);
        if (generation != old.generation) {
            wake();
            reporter.report("native_capture_demand active=" + active + " uid=" + uid + " session=" + session
                    + " generation=" + generation + " stopWhenMuted=" + stopWhenMuted, null);
            publish(new float[6], 0, false);
        }
    }
    private void serve() {
        try {
            LocalServerSocket listener = new LocalServerSocket(NativeSpectrumProtocol.SOCKET);
            server = listener;
            if (closed) { closeServer(listener); return; }
            reporter.report("native_results_listen abi=2 expectedBuild=" + BuildConfig.NATIVE_TAP_BUILD, null);
            while (!closed) {
                LocalSocket connection = listener.accept();
                try {
                    Credentials peer = connection.getPeerCredentials();
                    if (peer.getUid() != 0 || peer.getPid() <= 1) continue;
                    connection.setSoTimeout(200); client = connection;
                    consume(connection);
                } catch (IOException | RuntimeException e) {
                    if (!closed) reporter.report("native_results_disconnected", e);
                } finally {
                    if (client == connection) client = null;
                    try { connection.close(); } catch (IOException ignored) { }
                    publish(new float[6], 0, false);
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!closed) reporter.report("native_results_unavailable", e);
        } finally {
            LocalServerSocket listener = server; server = null;
            if (listener != null) closeServer(listener);
        }
    }
    private void consume(LocalSocket connection) throws IOException {
        InputStream input = connection.getInputStream();
        byte[] packet = new byte[NativeSpectrumProtocol.SIZE];
        int offset = 0; long connected = SystemClock.uptimeMillis(), lastPacket = connected;
        NativeSpectrumProtocol.Hello hello = null;
        long nextRequest = 0, requestSequence = 0, previousFrame = 0, frameGeneration = 0;
        int previousTrack = 0, previousIo = 0;
        long lastSignal = 0;
        boolean signalHealthy = false;
        StructPollfd readiness = new StructPollfd();
        readiness.fd = connection.getFileDescriptor(); readiness.events = (short) OsConstants.POLLIN;
        FileDescriptor[] pipe;
        try { pipe = Os.pipe(); } catch (ErrnoException e) { throw e.rethrowAsIOException(); }
        StructPollfd wakeup = new StructPollfd();
        wakeup.fd = pipe[0]; wakeup.events = (short) OsConstants.POLLIN;
        StructPollfd[] pollfds = {readiness, wakeup};
        byte[] drain = new byte[16];
        wakeWrite = pipe[1];
        try {
            while (!closed) {
                long now = SystemClock.uptimeMillis();
                // A companion heartbeat does not prove that PCM is still arriving.
                // Mark the signal unavailable once the last accepted PCM expires.
                if (signalHealthy && now - lastSignal >= 250) {
                    signalHealthy = false;
                    publish(new float[6], 0, false);
                }
                if (now - lastPacket > 2000) throw new IOException("Native heartbeat expired");
                if (hello != null && now >= nextRequest) {
                    Control c = control;
                    connection.getOutputStream().write(NativeSpectrumProtocol.request(hello.instance(), c.generation,
                            ++requestSequence, c.uid, c.session, requested && c.ready && c.generation == revision.get()));
                    nextRequest = now + (requested ? ACTIVE_MS : IDLE_MS);
                }
                // LocalSocket's native read reports EAGAIN as a generic IOException,
                // not necessarily SocketTimeoutException. Poll explicitly so idle
                // connections can renew control without disconnect/reconnect loops.
                // Active: 100 ms keeps the 250 ms signal expiry responsive. Idle: sleep
                // until the next lease renewal unless a demand change wakes the loop.
                int timeout = hello == null || requested || signalHealthy ? 100
                        : (int) Math.max(1, Math.min(IDLE_MS, nextRequest - SystemClock.uptimeMillis()));
                try {
                    if (Os.poll(pollfds, timeout) == 0) continue;
                    if ((wakeup.revents & OsConstants.POLLIN) != 0) {
                        wakePending.set(false);
                        Os.read(pipe[0], drain, 0, drain.length);
                        nextRequest = 0;
                        if ((readiness.revents & OsConstants.POLLIN) == 0) continue;
                    }
                } catch (ErrnoException e) { throw e.rethrowAsIOException(); }
                if ((readiness.revents & (OsConstants.POLLERR | OsConstants.POLLNVAL)) != 0)
                    throw new IOException("Native socket error");
                int count = input.read(packet, offset, packet.length - offset);
                if (count < 0) throw new IOException("Native EOF");
                offset += count;
                if (offset != packet.length) continue;
                offset = 0; lastPacket = SystemClock.uptimeMillis();
                if (hello == null) {
                    hello = NativeSpectrumProtocol.hello(packet, BuildConfig.NATIVE_TAP_BUILD);
                    revision.incrementAndGet(); execute(this::refresh);
                    reporter.report("native_results_connected producerPid=" + hello.pid()
                            + " instance=" + Long.toUnsignedString(hello.instance(), 16), null);
                    continue;
                }
                NativeSpectrumProtocol.Frame f = NativeSpectrumProtocol.frame(packet);
                Control c = control;
                if (f.instance() != hello.instance()) throw new IOException("Native instance changed");
                if (!requested || !c.ready || c.generation != revision.get()) continue;
                long threshold = frameGeneration != f.generation() || previousTrack != f.track() || previousIo != f.io() ? 0 : previousFrame;
                if (!NativeSpectrumProtocol.usable(f, hello.instance(), c.generation, c.uid, c.session, lastPacket, threshold)) continue;
                long time = f.nanos() / 1000000;
                if (frameGeneration != f.generation() || previousTrack != f.track() || previousIo != f.io()) {
                    frameGeneration = f.generation(); previousTrack = f.track(); previousFrame = 0;
                    previousIo = f.io();
                }
                if (f.sequence() <= previousFrame) continue;
                previousFrame = f.sequence();
                float[] levels = new float[6];
                for (int i = 0; i < 6; i++) levels[i] = SpectrumDsp.nativeResponse(i, f.bands()[i]);
                publish(levels, time, true, f.generation());
                lastSignal = time; signalHealthy = true;
            }
        } finally {
            wakeWrite = null;
            try { Os.close(pipe[0]); } catch (ErrnoException ignored) { }
            try { Os.close(pipe[1]); } catch (ErrnoException ignored) { }
        }
    }
    private void publish(float[] levels, long time, boolean healthy) { publish(levels, time, healthy, 0); }
    private synchronized void publish(float[] levels, long time, boolean healthy, long generation) {
        if (closed) return;
        latest = levels; latestTime = time; latestHealthy = healthy; latestGeneration = generation;
        if (!deliveryQueued) { deliveryQueued = true; main.post(deliver); }
    }
    private void deliver() {
        float[] levels; long time; boolean healthy;
        synchronized (this) {
            deliveryQueued = false; if (closed) return;
            levels = latest; time = latestTime; healthy = latestHealthy && requested
                    && latestGeneration == revision.get() && SystemClock.uptimeMillis() - time < 250;
        }
        sink.frame(levels, time, healthy);
    }
    private static void closeServer(LocalServerSocket socket) {
        var descriptor = socket.getFileDescriptor();
        if (descriptor != null) {
            try { Os.shutdown(descriptor, OsConstants.SHUT_RDWR); } catch (ErrnoException ignored) { }
        }
        try { socket.close(); } catch (IOException ignored) { }
    }
    @Override public void close() {
        closed = true; requested = false; revision.incrementAndGet();
        worker.removeCallbacksAndMessages(null); main.removeCallbacks(deliver);
        LocalSocket connection = client;
        if (connection != null) {
            try { connection.shutdownInput(); connection.shutdownOutput(); } catch (IOException ignored) { }
            finally { try { connection.close(); } catch (IOException ignored) { } }
        }
        LocalServerSocket listener = server;
        if (listener != null) closeServer(listener);
        thread.quitSafely();
        try { io.join(1500); thread.join(1500); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Native capture retirement interrupted", e); }
        if (io.isAlive() || thread.isAlive()) throw new IllegalStateException("Native capture worker did not retire");
    }
}
