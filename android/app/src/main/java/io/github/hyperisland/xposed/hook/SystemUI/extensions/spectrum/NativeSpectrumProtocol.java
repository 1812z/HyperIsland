// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0
package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Fixed size, little endian result/control protocol. Contains no raw PCM. */
final class NativeSpectrumProtocol {
    static final int MAGIC = 0x48535450, VERSION = 2, SIZE = 128;
    static final String SOCKET = "hyperspectrum.tap.v2";
    record Hello(long instance, String build, int health, int pid) { }
    record Frame(long instance, long generation, long sequence, long nanos, int uid, int session,
                 int io, boolean healthy, float[] bands, float rms, int rate, int track,
                 int channels, long callbacks, long fftFrames, long drops) { }

    private static ByteBuffer packet(byte[] bytes, int type) {
        if (bytes.length != SIZE) throw new IllegalArgumentException("Invalid native packet size");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getInt() != VERSION || b.getInt() != type || b.getInt() != SIZE)
            throw new IllegalArgumentException("Invalid native protocol header");
        return b;
    }
    static Hello hello(byte[] bytes, String expectedBuild) {
        ByteBuffer b = packet(bytes, 1);
        long instance = b.getLong();
        String build = new String(bytes, 32, 64, StandardCharsets.US_ASCII);
        int health = b.getInt(96), pid = b.getInt(100);
        if (instance == 0 || health != 4 || pid <= 1 || !build.matches("[0-9a-f]{64}") || !build.equals(expectedBuild))
            throw new IllegalArgumentException("Native producer build/health mismatch");
        return new Hello(instance, build, health, pid);
    }
    static byte[] request(long instance, long generation, long sequence, int uid, int session, boolean active) {
        ByteBuffer b = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(MAGIC).putInt(VERSION).putInt(2).putInt(SIZE);
        b.putLong(instance).putLong(generation).putLong(sequence).putLong(0);
        // I/O handles change on reboot and output recreation. Zero requests
        // the unique eligible track for this UID/session; replies carry its I/O.
        b.putInt(uid).putInt(session).putInt(0).putInt(active ? 1 : 0);
        return b.array();
    }
    static Frame frame(byte[] bytes) {
        ByteBuffer b = packet(bytes, 3);
        long instance = b.getLong(), generation = b.getLong(), sequence = b.getLong(), nanos = b.getLong();
        int uid = b.getInt(), session = b.getInt(), io = b.getInt(), flags = b.getInt();
        float[] bands = new float[6];
        for (int i = 0; i < bands.length; i++) {
            bands[i] = b.getFloat();
            if (!Float.isFinite(bands[i]) || bands[i] < 0 || bands[i] > 64)
                throw new IllegalArgumentException("Invalid native band amplitude");
        }
        float rms = b.getFloat();
        int rate = b.getInt(), track = b.getInt(), channels = b.getInt();
        long callbacks = b.getLong(), frames = b.getLong(), drops = b.getLong();
        if (!Float.isFinite(rms) || rms < 0 || rms > 32 || flags < 0 || flags > 1 ||
                (flags == 1 && (rate < 8000 || rate > 192000 || track <= 0 ||
                        channels < 1 || channels > 12 || sequence <= 0 || nanos <= 0)))
            throw new IllegalArgumentException("Invalid native frame metadata");
        return new Frame(instance, generation, sequence, nanos, uid, session, io, flags == 1,
                bands, rms, rate, track, channels, callbacks, frames, drops);
    }
    static boolean usable(Frame f, long instance, long generation, int uid, int session,
                          long nowMillis, long previousSequence) {
        long time = f.nanos() / 1000000;
        return f.healthy() && f.instance() == instance && f.generation() == generation &&
                f.uid() == uid && f.session() == session && f.io() > 0 &&
                f.sequence() > previousSequence && time <= nowMillis + 50 && nowMillis - time <= 250;
    }
    private NativeSpectrumProtocol() { }
}
