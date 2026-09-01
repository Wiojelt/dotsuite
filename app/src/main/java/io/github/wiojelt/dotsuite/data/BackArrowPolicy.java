package io.github.wiojelt.dotsuite.data;

/** An explicit wire allow-list, shared by the bridge, UI and native drawing hook. */
public final class BackArrowPolicy {
    private BackArrowPolicy() {}
    public static final String ENABLED = PersonalizationPolicy.PREFIX + "back_arrow_enabled";
    public static final String STYLE = PersonalizationPolicy.PREFIX + "back_arrow_style";
    public static final String MOTION = PersonalizationPolicy.PREFIX + "back_arrow_motion";
    public static final String SIZE = PersonalizationPolicy.PREFIX + "back_arrow_size";
    public static final String[] KEYS = { ENABLED, STYLE, MOTION, SIZE };
    public static boolean owns(String key) {
        return ENABLED.equals(key) || STYLE.equals(key) || MOTION.equals(key) || SIZE.equals(key);
    }
    public static boolean accepts(String key, String value) {
        if (!owns(key)) return false;
        if (value == null) return true;
        if (!value.matches("[0-9]{1,3}")) return false;
        int v = Integer.parseInt(value);
        if (ENABLED.equals(key)) return v <= 1;
        if (STYLE.equals(key)) return v <= 15;
        if (MOTION.equals(key)) return v <= 11;
        return v >= 80 && v <= 120;
    }
    public static float progress(float length, float reference) {
        if (!Float.isFinite(length) || !Float.isFinite(reference) || reference <= 0) return 0;
        return Math.max(0, Math.min(1, length / reference));
    }
    public static float motionScale(int motion, float progress) {
        float p = Float.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
        if (motion == 1) return .65f + .35f * p;
        if (motion == 3) return 1 - .15f * (float) Math.sin(Math.PI * p);
        return 1;
    }
}
