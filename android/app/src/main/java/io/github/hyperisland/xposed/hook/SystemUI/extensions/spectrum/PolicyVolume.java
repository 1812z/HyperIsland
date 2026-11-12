// Copyright 2026 wxxsfxyzm
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse only a uniquely identified capture output's applied music attenuation. */
final class PolicyVolume {
    record Reading(float db, int output, String reason) {
        static Reading unavailable() { return new Reading(Float.NaN, -1, "unavailable"); }
    }
    private static final Pattern GROUP = Pattern.compile("(?m)^\\s+-[^\\n]+ \\(id: (\\d+)\\)\\s*$");
    private static final Pattern OUTPUT = Pattern.compile("(?m)^  \\d+\\. Port ID: \\d+; I/O handle: (\\d+);[^\\n]*$");
    private static final Pattern DEVICE = Pattern.compile("(?m)^\\s*Devices: \\{AUDIO_DEVICE_OUT_BLUETOOTH_A2DP(?:_HEADPHONES|_SPEAKER)?, @:([^}]*)\\}\\s*$");
    private static final Pattern CLIENT = Pattern.compile("(?m)^      \\d+\\. Port ID: \\d+; Session ID: (\\d+); uid (\\d+); State: (Active|Inactive)\\s*$");
    private static final Pattern EFFECT = Pattern.compile("(?m)^  \\d+\\. Effect ID: \\d+; Attached to I/O handle: (\\d+); Session: (-?\\d+);\\s*\\n[^\\n]*\"Visualizer\"; Enabled; Active\\s*$");

    static Reading read(String text, String address, int uid, int captureSession) {
        if (uid < 0 || captureSession < 0 || address == null || address.isEmpty()) return Reading.unavailable();
        int groupsStart = text.indexOf("Volume Groups dump:");
        if (groupsStart < 0) return Reading.unavailable();
        String groups = text.substring(groupsStart);
        Matcher group = GROUP.matcher(groups);
        int musicGroup = -1;
        while (group.find()) {
            int start = group.end(), end = groups.length();
            Matcher next = GROUP.matcher(groups); if (next.find(start)) end = next.start();
            if (!groups.substring(start, end).contains("Streams: AUDIO_STREAM_MUSIC(3)")) continue;
            if (musicGroup >= 0) return Reading.unavailable();
            musicGroup = Integer.parseInt(group.group(1));
        }
        if (musicGroup < 0) return Reading.unavailable();
        HashSet<Integer> captureOutputs = new HashSet<>();
        Matcher effect = EFFECT.matcher(text);
        while (effect.find()) {
            if (Integer.parseInt(effect.group(2)) == captureSession) captureOutputs.add(Integer.parseInt(effect.group(1)));
        }
        // A session-zero effect on a standby/different mixer is not evidence for this route.
        if (captureOutputs.size() != 1) return Reading.unavailable();
        int captureOutput = captureOutputs.iterator().next();
        int outputsStart = text.indexOf(" Outputs ("), outputsEnd = text.indexOf(" Inputs (", outputsStart);
        if (outputsStart < 0 || outputsEnd < 0) return Reading.unavailable();
        String outputs = text.substring(outputsStart, outputsEnd);
        Matcher output = OUTPUT.matcher(outputs);
        while (output.find()) {
            if (Integer.parseInt(output.group(1)) != captureOutput) continue;
            int start = output.end(), end = outputs.length();
            Matcher next = OUTPUT.matcher(outputs); if (next.find(start)) end = next.start();
            String block = outputs.substring(start, end);
            Matcher device = DEVICE.matcher(block);
            if (!device.find() || !address.equalsIgnoreCase(device.group(1))) return Reading.unavailable();
            HashSet<Integer> sessions = new HashSet<>();
            Matcher client = CLIENT.matcher(block);
            while (client.find()) {
                if (Integer.parseInt(client.group(2)) != uid || !client.group(3).equals("Active")) continue;
                int session = Integer.parseInt(client.group(1));
                int clientStart = client.end(), clientEnd = block.length();
                Matcher nextClient = CLIENT.matcher(block); if (nextClient.find(clientStart)) clientEnd = nextClient.start();
                String details = block.substring(clientStart, clientEnd);
                if (session <= 0 || !details.contains("Usage: AUDIO_USAGE_MEDIA ")
                        || !details.contains("Stream: 3;") || !details.contains("InternalMute: No")) continue;
                if (captureSession > 0 && session != captureSession) continue;
                sessions.add(session);
            }
            if (sessions.size() != 1) return Reading.unavailable();
            Pattern volume = Pattern.compile("(?m)^\\s+id " + musicGroup
                    + ": - ActivityCount: ([1-9]\\d*),[^\\n]*, Volume: (-?\\d+(?:\\.\\d+)?), MuteCount: 0+\\s*$");
            Matcher applied = volume.matcher(block);
            if (!applied.find()) return Reading.unavailable();
            float db = Float.parseFloat(applied.group(2));
            if (applied.find() || !Float.isFinite(db) || db < -96 || db > 0) return Reading.unavailable();
            return new Reading(db, captureOutput, "applied");
        }
        return Reading.unavailable();
    }
}
