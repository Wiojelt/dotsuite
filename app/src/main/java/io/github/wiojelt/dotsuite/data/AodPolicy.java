package io.github.wiojelt.dotsuite.data;

import java.util.List;

/** Native settings only. No brightness, sensor, doze-state or power-saving overrides. */
public final class AodPolicy {
    private AodPolicy() {}
    public static final String ENABLED = "doze_always_on";
    public static final String MODE = "aod_display_mode";
    public static final String NOTIFICATIONS = "doze_enabled";
    public static final String TAP = "doze_tap_gesture";
    public static final String DOUBLE_TAP = "doze_pulse_on_double_tap";
    public static final String LIFT = "wake_gesture_enabled";
    public static final List<String> KEYS = List.of(ENABLED, MODE, NOTIFICATIONS, TAP, DOUBLE_TAP, LIFT);
    public static boolean owns(String key) { return KEYS.contains(key); }
    public static boolean accepts(String key, String value) {
        if (!owns(key)) return false;
        if (value == null) return true;
        return MODE.equals(key) ? value.matches("[0-2]") : value.equals("0") || value.equals("1");
    }
    public static boolean enabled(String raw, boolean nativeDefault) {
        return raw == null ? nativeDefault : !"0".equals(raw);
    }
    public static boolean canUseMode(int mode, boolean allDay, boolean schedule, boolean tap) {
        return mode == 0 ? allDay : mode == 1 ? schedule : mode == 2 && tap;
    }
    /** For status only. Scheduling and overnight transitions remain owned by Nothing OS. */
    public static boolean insideWindow(int minute, int start, int end) {
        if (minute < 0 || minute >= 1440 || start < 0 || start >= 1440 || end < 0 || end >= 1440)
            throw new IllegalArgumentException("Minutes out of range");
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }
    public static Integer minute(String hhmm) {
        if (hhmm == null || !hhmm.matches("([01][0-9]|2[0-3])[0-5][0-9]")) return null;
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(2));
    }
}
