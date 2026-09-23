package com.andulf.aiden;

final class BrewPolicy {
    // Old completion telemetry should not replace Ready when opening the app.
    static String sessionStage(boolean connected, boolean brewing, long start, long end, String value, boolean error, long now, long observedStart) {
        String phase = stage(connected, brewing, start, end, value, error, now);
        return "Complete".equals(phase) && start != observedStart ? "Ready" : phase;
    }
    static long remainingSeconds(long endSeconds, long nowMillis) {
        return Math.max(0L, (long)Math.floor(endSeconds - nowMillis / 1000.0));
    }
    static boolean active(boolean brewing, long start, long end) {
        return active(brewing, start, end, System.currentTimeMillis() / 1000);
    }
    static boolean active(boolean brewing, long start, long end, long now) {
        return brewing && !completed(start, end, now);
    }
    static boolean completed(long start, long end, long now) {
        return start > 0 && end > start && end <= now;
    }
    static String stage(boolean connected, boolean brewing, long start, long end, String value, boolean error) {
        return stage(connected, brewing, start, end, value, error, System.currentTimeMillis() / 1000);
    }
    static String stage(boolean connected, boolean brewing, long start, long end, String value, boolean error, long now) {
        if (!connected) return "Offline";
        if (error) return "Attention";
        if (completed(start, end, now)) return "Complete";
        if (!active(brewing, start, end, now)) return "Ready";
        if ("pa".equals(value)) return "Paused";
        if ("b".equals(value)) return "Bloom";
        if ("d".equals(value)) return "Drip finish";
        if ("bc".equals(value)) return "Complete";
        if (value.matches("p[0-9]+")) return "Pulse " + value.substring(1);
        if ("pr".equals(value)) return "Pouring";
        return "Brewing";
    }
}
