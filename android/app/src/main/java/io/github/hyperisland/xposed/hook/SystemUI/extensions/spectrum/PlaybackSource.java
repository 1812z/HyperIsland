// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.Spatializer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Worker-only, read-only session discovery. No output-stage or device effects. */
final class PlaybackSource {
    record State(int sessionId, int uid, boolean spatial, String reason, int sourceChannels, int captureChannels) {
        State(int sessionId, int uid, boolean spatial, String reason) {
            this(sessionId, uid, spatial, reason, 0, 0);
        }
    }
    private final AudioManager audio;
    private final PackageManager packages;
    private String packageName;
    private int userId = -1, uid = -1;
    private Method clientUid, sessionId, playerState;

    PlaybackSource(Context context, AudioManager audio) {
        this.audio = audio;
        packages = context.getPackageManager();
    }

    void media(String name, int user) throws ReflectiveOperationException {
        if (Objects.equals(packageName, name) && userId == user && uid >= 0) return;
        packageName = name; userId = user; uid = -1;
        if (name == null || name.isEmpty() || user < 0) return;
        // Resolve the notification's user, including work/secondary profiles. Never derive a UID
        // from an opaque notification key or assume the host Context belongs to that user.
        uid = (Integer) PackageManager.class.getMethod("getPackageUidAsUser", String.class, int.class)
                .invoke(packages, name, user);
    }

    State read() throws ReflectiveOperationException { return read(false); }

    State readNative() throws ReflectiveOperationException { return read(true); }

    private State read(boolean nativeCapture) throws ReflectiveOperationException {
        Spatializer spatializer = audio.getSpatializer();
        boolean spatial = spatializer != null && spatializer.isEnabled() && spatializer.isAvailable();
        if (!spatial && !nativeCapture) return new State(0, uid, false, "output_mix");
        if (uid < 0) return new State(-1, uid, true, "media_uid_unavailable");
        if (clientUid == null || sessionId == null || playerState == null) {
            clientUid = AudioPlaybackConfiguration.class.getMethod("getClientUid");
            sessionId = AudioPlaybackConfiguration.class.getMethod("getSessionId");
            playerState = AudioPlaybackConfiguration.class.getMethod("getPlayerState");
        }
        List<AudioPlaybackConfiguration> configs = audio.getActivePlaybackConfigurations();
        ArrayList<AudioSessionSelector.Candidate> candidates = new ArrayList<>();
        for (AudioPlaybackConfiguration config : configs) {
            candidates.add(new AudioSessionSelector.Candidate((Integer) clientUid.invoke(config),
                    (Integer) sessionId.invoke(config), (Integer) playerState.invoke(config) == 2,
                    config.getAudioAttributes().getUsage() == AudioAttributes.USAGE_MEDIA));
        }
        AudioSessionSelector.Selection selected = AudioSessionSelector.select(uid, candidates);
        State state = new State(selected.sessionId(), uid, spatial, nativeCapture && selected.sessionId() > 0 ? "native_media_session" : selected.reason());
        if (nativeCapture || selected.sessionId() <= 0 || !spatial) return state;
        try { return channels(state, spatializer, configs, candidates); }
        catch (ReflectiveOperationException | RuntimeException unsupported) {
            // Capture can still work without format metadata. Do not guess a channel multiplier.
            return state;
        }
    }

    @SuppressWarnings("unchecked")
    private State channels(State state, Spatializer spatializer, List<AudioPlaybackConfiguration> configs,
                           List<AudioSessionSelector.Candidate> candidates) throws ReflectiveOperationException {
        int sourceChannels = 0;
        Method spatialized = AudioPlaybackConfiguration.class.getMethod("isSpatialized");
        Method channelMask = AudioPlaybackConfiguration.class.getMethod("getChannelMask");
        for (int i = 0; i < configs.size(); i++) {
            AudioSessionSelector.Candidate candidate = candidates.get(i);
            if (candidate.uid() != uid || candidate.sessionId() != state.sessionId()
                    || !candidate.active() || !candidate.media()) continue;
            if (!(Boolean) spatialized.invoke(configs.get(i))) return state;
            int channels = Integer.bitCount((Integer) channelMask.invoke(configs.get(i)));
            if (channels < 2 || (sourceChannels != 0 && sourceChannels != channels)) return state;
            sourceChannels = channels;
        }
        // AOSP Spatializer::getAudioInConfig selects the largest supported input layout.
        // The device regression confirms 7.1.4 (12 channels) at this session's Visualizer input.
        // This is layout metadata only; no audio is captured from the spatializer output stage.
        List<Integer> masks = (List<Integer>) Spatializer.class.getMethod("getSpatializedChannelMasks")
                .invoke(spatializer);
        int captureChannels = masks.stream().mapToInt(Integer::bitCount).max().orElse(0);
        return new State(state.sessionId(), uid, true, state.reason(), sourceChannels, captureChannels);
    }
}
