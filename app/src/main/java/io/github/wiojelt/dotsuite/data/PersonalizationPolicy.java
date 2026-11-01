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
    public static final String TRANSLUCENT_QS = PREFIX + "translucent_qs";
    public static final String QS_TRANSPARENCY = PREFIX + "qs_transparency";
    public static final String QS_TILE_STYLE = PREFIX + "qs_tile_style";
    public static final String QS_ANIMATION_STYLE = PREFIX + "qs_anim_style";
    public static final String LAUNCHER_GRID_ROWS = PREFIX + "launcher_grid_rows";
    public static final String LAUNCHER_GRID_COLS = PREFIX + "launcher_grid_cols";
    public static final String LAUNCHER_ICON_SCALE = PREFIX + "launcher_icon_scale";
    public static final String LAUNCHER_GLASS_ICONS = PREFIX + "launcher_glass_icons";
    public static final String LAUNCHER_ICON_ALPHA = PREFIX + "launcher_icon_alpha";
    public static final String LAUNCHER_CUSTOM_ICONS = PREFIX + "launcher_custom_icons";
    public static final String DUO_STATUSBAR = PREFIX + "duo_statusbar";
    public static final String DUO_BATTERY_PERCENT = PREFIX + "duo_battery_percent";
    public static final String NETWORK_TRAFFIC = PREFIX + "network_traffic";
    public static final String SCREEN_OFF_ANIM = PREFIX + "screen_off_anim";
    public static final String SCRAMBLE_PIN = PREFIX + "scramble_pin";
    public static final String HIDE_PIN_INPUT = PREFIX + "hide_pin_input";
    public static final String LOCKSCREEN_CHARGING_INFO = PREFIX + "lockscreen_charging_info";
    public static final String BATTERY_CAP_ENABLED = PREFIX + "battery_cap_enabled";
    public static final String BATTERY_CAP_LEVEL = PREFIX + "battery_cap_level";
    public static final String ADVANCED_POWER_MENU = PREFIX + "advanced_power_menu";
    public static final String STATUS_BAR_BRIGHTNESS = PREFIX + "status_bar_brightness";
    public static final String STATUS_PADDING_START = PREFIX + "status_padding_start";
    public static final String STATUS_PADDING_END = PREFIX + "status_padding_end";
    public static final String BACK_GESTURE_KILL = PREFIX + "back_gesture_kill";
    public static final String BYPASS_FLAG_SECURE = PREFIX + "bypass_flag_secure";
    public static final String VOLUME_PANEL_TIMEOUT = PREFIX + "volume_panel_timeout";
    public static final String DISABLE_SAFE_VOLUME = PREFIX + "disable_safe_volume";
    public static final String EARPHONE_ICON = PREFIX + "earphone_icon";
    public static final String BACK_GESTURE_TIMEOUT = PREFIX + "back_gesture_timeout";
    public static final String TORCH_LEVEL = PREFIX + "torch_level";
    public static final String CAFFEINATE_TIMEOUT = PREFIX + "caffeinate_timeout";
    public static final String LAUNCHER_ICON_SHAPE = PREFIX + "launcher_icon_shape";
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
                || STATUS_DOUBLE_SLEEP.equals(key) || TRANSLUCENT_QS.equals(key)
                || BATTERY_CAP_ENABLED.equals(key) || ADVANCED_POWER_MENU.equals(key)
                || LAUNCHER_GLASS_ICONS.equals(key) || DUO_STATUSBAR.equals(key)
                || DUO_BATTERY_PERCENT.equals(key) || NETWORK_TRAFFIC.equals(key)
                || SCRAMBLE_PIN.equals(key) || HIDE_PIN_INPUT.equals(key)
                || LOCKSCREEN_CHARGING_INFO.equals(key)
                || STATUS_BAR_BRIGHTNESS.equals(key) || BACK_GESTURE_KILL.equals(key)
                || BYPASS_FLAG_SECURE.equals(key) || DISABLE_SAFE_VOLUME.equals(key)
                || EARPHONE_ICON.equals(key) || HIDE_NAV_PILL.equals(key)) return value == 0 || value == 1;
        if (STATUS_PADDING_START.equals(key) || STATUS_PADDING_END.equals(key)) return value >= 0 && value <= 48;
        if (VOLUME_PANEL_TIMEOUT.equals(key)) return value >= 500 && value <= 10000;
        if (BACK_GESTURE_TIMEOUT.equals(key)) return value >= 200 && value <= 1000;
        if (TORCH_LEVEL.equals(key)) return value >= 1 && value <= 10;
        if (CAFFEINATE_TIMEOUT.equals(key)) return value >= -1 && value <= 60;
        if (LAUNCHER_ICON_SHAPE.equals(key)) return value >= 0 && value <= 3;
        if (BATTERY_CAP_LEVEL.equals(key) || QS_TRANSPARENCY.equals(key) || LAUNCHER_ICON_ALPHA.equals(key)) return value >= 0 && value <= 100;
        if (QS_TILE_STYLE.equals(key) || QS_ANIMATION_STYLE.equals(key) || SCREEN_OFF_ANIM.equals(key)) return value >= 0 && value <= 4;
        if (LAUNCHER_GRID_ROWS.equals(key) || LAUNCHER_GRID_COLS.equals(key)) return value >= 3 && value <= 9;
        if (LAUNCHER_ICON_SCALE.equals(key)) return value >= 50 && value <= 150;
        if (NOTCH_TAP.equals(key) || NOTCH_DOUBLE.equals(key) || NOTCH_HOLD.equals(key)
                || NOTCH_LEFT.equals(key) || NOTCH_RIGHT.equals(key)) return isAction(value);
        return false;
    }

    public static String namespace(String key) {
        if (acceptsInt(key, 0) || acceptsInt(key, 1000) || acceptsInt(key, 1) || BATTERY_CAP_LEVEL.equals(key) || VOLUME_PANEL_TIMEOUT.equals(key)) return "secure";
        if (BackArrowPolicy.owns(key)) return "secure";
        if (AodPolicy.owns(key)) return "secure";
        if (CARRIER_LABEL.equals(key) || EXTRA_DIM.equals(key) || EXTRA_DIM_LEVEL.equals(key)
                || CHARGING_SOUNDS.equals(key) || CHARGING_VIBRATION.equals(key)
                || CLOCK_SECONDS.equals(key) || CLOCK_DAY.equals(key) || HIDE_NAV_PILL.equals(key)
                || LAUNCHER_CUSTOM_ICONS.equals(key)) return "secure";
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
        if (acceptsInt(key, 0) || acceptsInt(key, 1000) || VOLUME_PANEL_TIMEOUT.equals(key)) {
            try { return acceptsInt(key, Integer.parseInt(value)); }
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
