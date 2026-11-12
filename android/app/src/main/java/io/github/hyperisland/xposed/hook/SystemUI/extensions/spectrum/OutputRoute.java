// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.flagging.AconfigPackage;
import android.os.SystemClock;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Read-only platform queries. Connected devices alone do not identify the media output. */
final class OutputRoute {
    private static final AudioAttributes MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final AudioManager audio;
    private final AppliedVolume appliedVolume;
    private Constructor<?> attributes;
    private Method behavior;
    private Boolean noPrescale, indexFix;

    static final class State {
        final String key, reason;
        final int type, behavior, volume, maximum;
        final boolean muted;
        final float volumeDb, maximumDb;
        final float appliedDb;
        final int policyOutput;
        final VolumeCompensation.CaptureVolume mode;

        State(String key, String reason, int type, int behavior, int volume, int maximum,
              boolean muted, float volumeDb, float maximumDb, VolumeCompensation.CaptureVolume mode,
              float appliedDb, int policyOutput) {
            this.key = key; this.reason = reason; this.type = type; this.behavior = behavior;
            this.volume = volume; this.maximum = maximum; this.muted = muted;
            this.volumeDb = volumeDb; this.maximumDb = maximumDb; this.mode = mode;
            this.appliedDb = appliedDb; this.policyOutput = policyOutput;
        }

        double gain() { return VolumeCompensation.appliedGain(mode, volumeDb, maximumDb, appliedDb); }
    }

    OutputRoute(AudioManager audio) { this.audio = audio; appliedVolume = new AppliedVolume(); }

    State read(int uid, int captureSession, long generation) {
        int volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        boolean muted = volume <= 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC);
        String key = "unknown";
        String reason = "route_unavailable";
        int type = 0, volumeBehavior = -1;
        float db = Float.NaN, maxDb = Float.NaN;
        float appliedDb = Float.NaN;
        int policyOutput = -1;
        VolumeCompensation.CaptureVolume mode = VolumeCompensation.CaptureVolume.UNKNOWN;
        try {
            List<AudioDeviceInfo> devices = audio.getAudioDevicesForAttributes(MEDIA);
            // A duplicated output cannot be represented by one attenuation coefficient.
            if (devices.size() != 1) {
                key = "outputs:" + devices.stream().map(d -> d.getId() + ":" + d.getType())
                        .sorted().reduce("", (a, b) -> a + "/" + b);
                reason = "output_count_" + devices.size();
            } else {
                AudioDeviceInfo device = devices.get(0);
                type = device.getType();
                key = device.getId() + ":" + type;
                if (device.isSink() && (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) {
                    int curveType = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            ? AudioDeviceInfo.TYPE_BLUETOOTH_A2DP : AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
                    db = audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, volume, curveType);
                    maxDb = audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, maximum, curveType);
                }
                if (attributes == null || behavior == null) {
                    Class<?> cls = Class.forName("android.media.AudioDeviceAttributes");
                    attributes = cls.getConstructor(AudioDeviceInfo.class);
                    behavior = AudioManager.class.getMethod("getDeviceVolumeBehavior", cls);
                }
                volumeBehavior = (Integer) behavior.invoke(audio, attributes.newInstance(device));
                key += ":" + volumeBehavior;
                if (volumeBehavior == 0) {
                    mode = VolumeCompensation.CaptureVolume.SOFTWARE;
                    reason = "software_volume";
                } else if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && volumeBehavior == 3) {
                    // Legacy A2DP may pre-scale its first three volume steps. Do not attenuate twice.
                    if (noPrescale == null || indexFix == null) {
                        // The generated audio Flags class belongs to system_server on some ROMs.
                        // Read named platform flags without importing that process's classes.
                        AconfigPackage flags = AconfigPackage.load("com.android.media.audio");
                        noPrescale = flags.getBooleanFlagValue("disable_prescale_absolute_volume", false);
                        indexFix = flags.getBooleanFlagValue("abs_volume_index_fix", false);
                    }
                    if (Boolean.TRUE.equals(noPrescale) || Boolean.TRUE.equals(indexFix)) {
                        mode = VolumeCompensation.CaptureVolume.EXTERNAL;
                        reason = Boolean.TRUE.equals(indexFix) ? "absolute_a2dp_policy" : "absolute_a2dp_no_prescale";
                    } else {
                        reason = "legacy_prescale";
                    }
                } else reason = "unsupported_volume_behavior";
                if (mode == VolumeCompensation.CaptureVolume.SOFTWARE
                        && (!Float.isFinite(maxDb) || Float.isNaN(db)
                            || db == Float.POSITIVE_INFINITY || db > maxDb)) {
                    mode = VolumeCompensation.CaptureVolume.UNKNOWN;
                    reason = "invalid_volume_curve";
                }
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        && mode != VolumeCompensation.CaptureVolume.UNKNOWN) {
                    PolicyVolume.Reading applied = appliedVolume.read(key, device.getAddress(), volume,
                            uid, captureSession, generation, SystemClock.uptimeMillis());
                    appliedDb = applied.db(); policyOutput = applied.output();
                    reason += "+policy_" + applied.reason();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Retry on later queries; one unsupported call must not disable volume/mute checks.
            mode = VolumeCompensation.CaptureVolume.UNKNOWN;
            reason = "query_" + failure.getClass().getSimpleName();
        }
        return new State(key, reason, type, volumeBehavior, volume, maximum, muted, db, maxDb, mode,
                appliedDb, policyOutput);
    }
}
