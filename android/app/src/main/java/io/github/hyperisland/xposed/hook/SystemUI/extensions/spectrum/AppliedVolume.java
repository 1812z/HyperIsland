// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Worker-only Binder metadata query; no shell, root, audio capture or retained dump. */
final class AppliedVolume {
    private String key;
    private long queried;
    private PolicyVolume.Reading cached = PolicyVolume.Reading.unavailable();

    PolicyVolume.Reading read(String route, String address, int volume, int uid, int session, long generation, long now) {
        if (uid < 0 || session < 0) return unavailable("capture_pending");
        if (address == null || address.isEmpty()) return unavailable("route_address_unavailable");
        String nextKey = route + ":" + volume + ":" + uid + ":" + session + ":" + generation;
        if (nextKey.equals(key) && now - queried < (Float.isFinite(cached.db()) ? 3000 : 1000)) return cached;
        key = nextKey; queried = now; cached = PolicyVolume.Reading.unavailable();
        try {
            IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("checkService", String.class).invoke(null, "media.audio_policy");
            if (service == null) return cached = unavailable("service_unavailable");
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor reader = pipe[0]; ParcelFileDescriptor writer = pipe[1]) {
                // Standard asynchronous Binder dump into a pipe: no app file is passed to the
                // audio service, and no second worker is needed to drain a synchronous dump.
                int flags = Os.fcntlInt(reader.getFileDescriptor(), OsConstants.F_GETFL, 0);
                Os.fcntlInt(reader.getFileDescriptor(), OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
                service.dumpAsync(writer.getFileDescriptor(), new String[0]);
                writer.close();
                StructPollfd descriptor = new StructPollfd();
                descriptor.fd = reader.getFileDescriptor(); descriptor.events = (short) OsConstants.POLLIN;
                StructPollfd[] descriptors = {descriptor};
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long deadline = SystemClock.uptimeMillis() + 500;
                while (true) {
                    long remaining = deadline - SystemClock.uptimeMillis();
                    if (remaining <= 0) return cached = unavailable("dump_timeout");
                    if (Os.poll(descriptors, (int) remaining) == 0) return cached = unavailable("dump_timeout");
                    int count;
                    try { count = Os.read(reader.getFileDescriptor(), buffer, 0, buffer.length); }
                    catch (ErrnoException interrupted) {
                        if (interrupted.errno == OsConstants.EAGAIN || interrupted.errno == OsConstants.EINTR) continue;
                        throw interrupted;
                    }
                    if (count == 0) break;
                    if (bytes.size() + count > 1024 * 1024) return cached = unavailable("dump_too_large");
                    bytes.write(buffer, 0, count);
                }
                if (bytes.size() == 0) return cached = unavailable("dump_empty");
                String text = bytes.toString(StandardCharsets.UTF_8.name());
                cached = PolicyVolume.read(text, address, uid, session);
                if (!Float.isFinite(cached.db())) cached = unavailable(text.contains("Permission Denial")
                        ? "dump_permission_denied" : "metadata_unavailable");
            }
        } catch (ReflectiveOperationException | java.io.IOException | android.os.RemoteException | ErrnoException
                 | RuntimeException failure) {
            cached = unavailable("query_" + failure.getClass().getSimpleName());
        }
        return cached;
    }

    private static PolicyVolume.Reading unavailable(String reason) {
        return new PolicyVolume.Reading(Float.NaN, -1, reason);
    }
}
