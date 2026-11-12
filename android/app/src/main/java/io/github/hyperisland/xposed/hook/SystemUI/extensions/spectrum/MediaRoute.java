// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0
package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import java.util.List;

/** Distinguish a local wired output from HyperOS's generic casting category. */
public final class MediaRoute {
    public static String resolve(Context context, String route, MediaSession.Token token) {
        if (!"others".equals(route) || token == null) return route;
        try {
            MediaController.PlaybackInfo playback = new MediaController(context, token).getPlaybackInfo();
            if (playback == null || playback.getPlaybackType() != MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL)
                return route;
            AudioManager audio = context.getSystemService(AudioManager.class);
            if (audio == null) return route;
            AudioAttributes attributes = playback.getAudioAttributes();
            if (attributes == null) return route;
            List<AudioDeviceInfo> outputs = audio.getAudioDevicesForAttributes(attributes);
            if (outputs.size() != 1 || !outputs.get(0).isSink()) return route;
            return classify(route, true, new int[]{outputs.get(0).getType()});
        } catch (RuntimeException unavailable) {
            // A connected USB device alone never authorizes replacing a cast icon.
            return route;
        }
    }

    static String classify(String route, boolean localPlayback, int[] outputTypes) {
        if (!"others".equals(route) || !localPlayback || outputTypes.length != 1) return route;
        return switch (outputTypes[0]) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> "wired";
            default -> route;
        };
    }

    public static boolean isLocal(String route) {
        return "".equals(route) || "local".equals(route) || "headset".equals(route) || "wired".equals(route);
    }

    private MediaRoute() { }
}
