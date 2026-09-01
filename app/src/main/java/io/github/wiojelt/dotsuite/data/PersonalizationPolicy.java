package io.github.wiojelt.dotsuite.data;

/** Shared, Android-free wire contract. Never accept arbitrary settings or shell commands. */
public final class PersonalizationPolicy {
    private PersonalizationPolicy() {}
    public static final String PREFIX = "dotsuite_";
    public static final String POWER_TORCH = PREFIX + "power_torch";
    public static final String POWER_TORCH_STATUS = PREFIX + "power_torch_status";
    public static final String HIDE_DIRECT_SHARE = PREFIX + "hide_direct_share";
    public static final String NOTCH_ENABLED = PREFIX + "notch_enabled";
    public static final String NOTCH_TAP = PREFIX + "notch_tap";
    public static final String NOTCH_DOUBLE = PREFIX + "notch_double";
    public static final String NOTCH_HOLD = PREFIX + "notch_hold";
    public static final String NOTCH_LEFT = PREFIX + "notch_left";
    public static final String NOTCH_RIGHT = PREFIX + "notch_right";
    public static final String NOTCH_HAPTICS = PREFIX + "notch_haptics";
    public static final String STATUS_DOUBLE_SLEEP = PREFIX + "status_double_sleep";
    public static final String CARRIER_LABEL = PREFIX + "carrier_label";
    public static final String WINDOW_SCALE = "window_animation_scale";
    public static final String TRANSITION_SCALE = "transition_animation_scale";
    public static final String ANIMATOR_SCALE = "animator_duration_scale";
    public static final String MASTER_MONO = "master_mono";
    public static final String MASTER_BALANCE = "master_balance";
    public static final String EXTRA_DIM = "reduce_bright_colors_activated";
    public static final String EXTRA_DIM_LEVEL = "reduce_bright_colors_level";
    public static final String TOUCH_SOUNDS = "sound_effects_enabled";
    public static final String LOCK_SOUNDS = "lockscreen_sounds_enabled";
    public static final String DIAL_SOUNDS = "dtmf_tone";
    public static final String CHARGING_SOUNDS = "charging_sounds_enabled";
    public static final String CHARGING_VIBRATION = "charging_vibration_enabled";
    public static final String CLOCK_SECONDS = "clock_seconds";
    public static final String CLOCK_DAY = PREFIX + "clock_day";
    public static final String HIDE_NAV_PILL = PREFIX + "hide_nav_pill";
    public static final String AUTO_ROTATE = "accelerometer_rotation";
    public static final String USER_ROTATION = "user_rotation";

    public static final int NONE = 0, SCREENSHOT = 1, FLASHLIGHT = 2,
            PLAY_PAUSE = 3, NEXT = 4, PREVIOUS = 5, VOLUME_PANEL = 6,
            NOTIFICATIONS = 7, QUICK_SETTINGS = 8, SLEEP = 9, CAMERA = 10,
            PHOTO_FRONT = 11, PHOTO_REAR = 12, VIDEO_FRONT = 13, VIDEO_REAR = 14, QUICK_DOCK = 15;

    public static boolean isCaptureAction(int value) { return value >= 11 && value <= 14; }
    public static boolean isVideo(int value) { return value == VIDEO_FRONT || value == VIDEO_REAR; }
    public static boolean isFront(int value) { return value == PHOTO_FRONT || value == VIDEO_FRONT; }
    public static boolean isAction(int value) { return value >= NONE && value <= QUICK_DOCK; }

    public static boolean acceptsInt(String key, int value) {
        if (POWER_TORCH.equals(key) || HIDE_DIRECT_SHARE.equals(key) || NOTCH_ENABLED.equals(key) || NOTCH_HAPTICS.equals(key)
                || STATUS_DOUBLE_SLEEP.equals(key)) return value == 0 || value == 1;
        if (NOTCH_TAP.equals(key) || NOTCH_DOUBLE.equals(key) || NOTCH_HOLD.equals(key)
                || NOTCH_LEFT.equals(key) || NOTCH_RIGHT.equals(key)) return isAction(value);
        return false;
    }

    public static String namespace(String key) {
        if (acceptsInt(key, 0)) return "secure";
        if (BackArrowPolicy.owns(key)) return "secure";
        if (AodPolicy.owns(key)) return "secure";
        if (CARRIER_LABEL.equals(key) || EXTRA_DIM.equals(key) || EXTRA_DIM_LEVEL.equals(key)
                || CHARGING_SOUNDS.equals(key) || CHARGING_VIBRATION.equals(key)
                || CLOCK_SECONDS.equals(key) || CLOCK_DAY.equals(key) || HIDE_NAV_PILL.equals(key)) return "secure";
        if (MASTER_MONO.equals(key) || MASTER_BALANCE.equals(key) || TOUCH_SOUNDS.equals(key)
                || LOCK_SOUNDS.equals(key) || DIAL_SOUNDS.equals(key)
                || AUTO_ROTATE.equals(key) || USER_ROTATION.equals(key)) return "system";
        if (WINDOW_SCALE.equals(key) || TRANSITION_SCALE.equals(key)
                || ANIMATOR_SCALE.equals(key)) return "global";
        return null;
    }

    public static boolean acceptsString(String key, String value) {
        if (BackArrowPolicy.owns(key)) return BackArrowPolicy.accepts(key, value);
        if (AodPolicy.owns(key)) return AodPolicy.accepts(key, value);
        if (namespace(key) == null) return false;
        if (value == null) return true; // restore a missing row by deleting exactly this key
        if (acceptsInt(key, 0)) {
            try { return value.matches("[0-9]{1,2}") && acceptsInt(key, Integer.parseInt(value)); }
            catch (NumberFormatException ignored) { return false; }
        }
        if (MASTER_MONO.equals(key) || EXTRA_DIM.equals(key) || TOUCH_SOUNDS.equals(key)
                || LOCK_SOUNDS.equals(key) || DIAL_SOUNDS.equals(key) || CHARGING_SOUNDS.equals(key)
                || CHARGING_VIBRATION.equals(key) || CLOCK_SECONDS.equals(key) || CLOCK_DAY.equals(key)
                || HIDE_NAV_PILL.equals(key) || AUTO_ROTATE.equals(key)) return "0".equals(value) || "1".equals(value);
        if (USER_ROTATION.equals(key)) return value.matches("[0-3]");
        if (EXTRA_DIM_LEVEL.equals(key)) {
            try { return value.matches("[0-9]{1,3}") && Integer.parseInt(value) <= 100; }
            catch (NumberFormatException ignored) { return false; }
        }
        if (MASTER_BALANCE.equals(key)) {
            try {
                float balance = Float.parseFloat(value);
                return Float.isFinite(balance) && balance >= -1f && balance <= 1f
                        && value.matches("-?[0-9]+(\\.[0-9]+)?");
            } catch (NumberFormatException ignored) { return false; }
        }
        if (CARRIER_LABEL.equals(key)) {
            // SettingsState normalizes this exact reserved token to SQL NULL.
            if ("null".equals(value)) return false;
            if (value.codePointCount(0, value.length()) > 32) return false;
            for (int i = 0; i < value.length();) {
                int cp = value.codePointAt(i);
                int type = Character.getType(cp);
                if (Character.isISOControl(cp) || type == Character.FORMAT
                        || type == Character.SURROGATE) return false;
                i += Character.charCount(cp);
            }
            return value.equals(value.trim());
        }
        try {
            float number = Float.parseFloat(value);
            return Float.isFinite(number) && number >= 0f && number <= 10f
                    && value.matches("[0-9]+(\\.[0-9]+)?");
        } catch (NumberFormatException ignored) { return false; }
    }

    public static boolean sameSetting(String key, String a, String b) {
        if (a == null || b == null) return a == b;
        if ("global".equals(namespace(key)) || MASTER_BALANCE.equals(key)) {
            try { return Float.compare(Float.parseFloat(a), Float.parseFloat(b)) == 0; }
            catch (NumberFormatException ignored) { return a.equals(b); }
        }
        return a.equals(b);
    }
}
