package io.github.ankushui18.fbaoffline;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Canonical, portable boot overrides. Native code also validates against the running driver's choices. */
final class CoreProfile {
    static final String PREFIX = "core:";
    static final String HISCORES = "fbneo-hiscores";
    private static final int MAX_BYTES = 2048;
    // Keep in sync with engine.cpp::bootRules; the native boundary is authoritative.
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "fbneo-cpu-speed-adjust", HISCORES, "fbneo-force-60hz", "fbneo-allow-depth-32",
            "fbneo-fixed-frameskip", "fbneo-samplerate", "fbneo-sample-interpolation",
            "fbneo-fm-interpolation", "fbneo-lowpass-filter", "fbneo-socd"));
    private static final Pattern VALUE = Pattern.compile("[ -~]{1,64}");

    private CoreProfile() {}

    static String key(String rom) { return PREFIX + rom; }

    static String encode(Map<String, String> choices) {
        if (choices.size() > KEYS.size()) throw invalid();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> choice : new TreeMap<>(choices).entrySet()) {
            if (!allowed(choice.getKey(), choice.getValue())) throw invalid();
            out.append(choice.getKey()).append('=').append(choice.getValue()).append('\n');
        }
        if (out.length() > MAX_BYTES) throw invalid();
        return out.toString();
    }

    static Map<String, String> decode(String profile) {
        if (profile == null || profile.length() > MAX_BYTES) throw invalid();
        Map<String, String> choices = new TreeMap<>();
        // The ordered, unique representation is also part of the save state's compatibility identity.
        String previous = "";
        for (int from = 0; from < profile.length();) {
            int end = profile.indexOf('\n', from), sep = profile.indexOf('=', from);
            if (end < 0 || sep < from || sep >= end) throw invalid();
            String key = profile.substring(from, sep), value = profile.substring(sep + 1, end);
            if (!allowed(key, value) || key.compareTo(previous) <= 0) throw invalid();
            choices.put(key, value);
            previous = key;
            from = end + 1;
        }
        return choices;
    }

    static boolean valid(String profile) {
        try { decode(profile); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean allowed(String key, String value) {
        return KEYS.contains(key) && value != null && VALUE.matcher(value).matches() && value.indexOf('=') < 0;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid FBNeo settings."); }
}
