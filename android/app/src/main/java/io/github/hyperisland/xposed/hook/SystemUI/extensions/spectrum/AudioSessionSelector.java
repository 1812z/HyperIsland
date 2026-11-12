// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import java.util.List;

/** Never guess between independent playback sessions or select another application's audio. */
final class AudioSessionSelector {
    record Candidate(int uid, int sessionId, boolean active, boolean media) { }
    record Selection(int sessionId, String reason) { }

    static Selection select(int uid, List<Candidate> candidates) {
        if (uid < 0) return new Selection(-1, "media_uid_unavailable");
        int selected = -1;
        for (Candidate candidate : candidates) {
            if (candidate.uid != uid || !candidate.active || !candidate.media || candidate.sessionId <= 0) continue;
            if (selected > 0 && selected != candidate.sessionId) return new Selection(-1, "ambiguous_media_sessions");
            selected = candidate.sessionId;
        }
        return new Selection(selected, selected > 0 ? "spatializer_media_session" : "no_active_media_session");
    }
}
