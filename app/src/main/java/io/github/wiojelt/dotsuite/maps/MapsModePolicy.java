package io.github.wiojelt.dotsuite.maps;

/** No notification text, route or address is needed to identify navigation. */
public final class MapsModePolicy {
    private MapsModePolicy() {}
    public static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    public static boolean isNavigation(String packageName, String category, boolean ongoing) {
        return MAPS_PACKAGE.equals(packageName)
                && "navigation".equals(category) && ongoing;
    }
    public static boolean shouldLaunch(boolean enabled, boolean navigation, boolean armed,
            boolean communicating, long now, long lastLaunch) {
        return enabled && navigation && armed && !communicating
                && (lastLaunch == 0 || now - lastLaunch >= 5000);
    }
}
