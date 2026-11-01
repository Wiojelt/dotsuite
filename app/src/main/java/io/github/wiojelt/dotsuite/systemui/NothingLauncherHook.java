package io.github.wiojelt.dotsuite.systemui;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * Nothing OS Launcher Hook (Home Up / Theme Park style customizer).
 * Customizes Nothing Launcher (com.nothing.launcher):
 * 1. InvariantDeviceProfile: Custom grid rows & columns (4x5, 5x5, 6x6, 7x7) and icon size multiplier.
 * 2. BubbleTextView: Translucent frosted glass / liquid glass icons matching Nothing OS translucent widgets.
 * 3. Custom icon overrides per application.
 */
final class NothingLauncherHook {
    private static final String TAG = "DotSuite.Launcher";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;
    private static ContentResolver contentResolver;

    // Cached configuration
    private static volatile int gridRows = 0;
    private static volatile int gridCols = 0;
    private static volatile int iconScalePercent = 100;
    private static volatile boolean glassIconsEnabled = false;
    private static volatile int iconAlpha = 255; // 0-255
    private static volatile String customIconsJson = "{}";
    private static final java.util.Map<String, String> CUSTOM_ICONS_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        XposedBridge.log(TAG + ": install() called for Nothing Launcher");

        hookLauncherApplication(loader);
        hookDeviceProfile(loader);
        hookBubbleTextView(loader);
    }

    private static void hookLauncherApplication(ClassLoader loader) {
        try {
            Class<?> appClass = XposedHelpers.findClassIfExists("android.app.Application", loader);
            if (appClass != null) {
                XposedBridge.hookAllMethods(appClass, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            initObserver((Context) param.thisObject);
                        } catch (Throwable t) {
                            report("initObserver in Application", t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            report("hookLauncherApplication", t);
        }
    }

    static void initObserver(Context context) {
        if (contentResolver != null || context == null) return;
        try {
            contentResolver = context.getApplicationContext().getContentResolver();
            updateSettings();

            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    updateSettings();
                }
            };

            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.LAUNCHER_GRID_ROWS), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.LAUNCHER_GRID_COLS), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.LAUNCHER_ICON_SCALE), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.LAUNCHER_GLASS_ICONS), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.LAUNCHER_ICON_ALPHA), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.LAUNCHER_CUSTOM_ICONS), false, observer);
            XposedBridge.log(TAG + ": ContentObservers registered successfully");
        } catch (Throwable t) {
            report("initObserver", t);
        }
    }

    private static void updateSettings() {
        if (contentResolver == null) return;
        try {
            gridRows = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.LAUNCHER_GRID_ROWS, 0);
            gridCols = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.LAUNCHER_GRID_COLS, 0);
            iconScalePercent = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.LAUNCHER_ICON_SCALE, 100);
            glassIconsEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.LAUNCHER_GLASS_ICONS, 0) == 1;
            int alphaSetting = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.LAUNCHER_ICON_ALPHA, 100);
            iconAlpha = (int) (alphaSetting * 2.55f);
            String iconsStr = Settings.Secure.getString(contentResolver, PersonalizationPolicy.LAUNCHER_CUSTOM_ICONS);
            if (iconsStr != null && !iconsStr.equals(customIconsJson)) {
                customIconsJson = iconsStr;
                CUSTOM_ICONS_MAP.clear();
                try {
                    org.json.JSONObject obj = new org.json.JSONObject(iconsStr);
                    java.util.Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        CUSTOM_ICONS_MAP.put(k, obj.getString(k));
                    }
                } catch (Throwable ignored) {}
            }
            XposedBridge.log(TAG + ": Settings updated: grid=" + gridRows + "x" + gridCols +
                    ", scale=" + iconScalePercent + "%, glass=" + glassIconsEnabled + ", alpha=" + iconAlpha);
        } catch (Throwable t) {
            report("updateSettings", t);
        }
    }

    private static void hookDeviceProfile(ClassLoader loader) {
        try {
            Class<?> invProfileClass = XposedHelpers.findClassIfExists(
                    "com.android.launcher3.InvariantDeviceProfile", loader);
            if (invProfileClass == null) {
                XposedBridge.log(TAG + ": InvariantDeviceProfile not found");
                return;
            }

            // Hook constructors of InvariantDeviceProfile
            XposedBridge.hookAllConstructors(invProfileClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        applyGridAndScale(param.thisObject);
                    } catch (Throwable t) {
                        report("InvariantDeviceProfile ctor", t);
                    }
                }
            });

            // Also hook any init / onConfigChanged methods
            XposedBridge.hookAllMethods(invProfileClass, "initGrid", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        applyGridAndScale(param.thisObject);
                    } catch (Throwable t) {
                        report("initGrid", t);
                    }
                }
            });
        } catch (Throwable t) {
            report("hookDeviceProfile", t);
        }
    }

    private static void applyGridAndScale(Object profile) {
        if (profile == null) return;
        try {
            if (gridRows >= 3 && gridRows <= 9) {
                XposedHelpers.setIntField(profile, "numRows", gridRows);
                try {
                    XposedHelpers.setIntField(profile, "numFolderRows", Math.min(gridRows, 5));
                } catch (Throwable ignored) {}
            }
            if (gridCols >= 3 && gridCols <= 9) {
                XposedHelpers.setIntField(profile, "numColumns", gridCols);
                try {
                    XposedHelpers.setIntField(profile, "numFolderColumns", Math.min(gridCols, 5));
                } catch (Throwable ignored) {}
            }
            if (iconScalePercent > 0 && iconScalePercent != 100) {
                float factor = iconScalePercent / 100.0f;
                try {
                    float currentIconSize = XposedHelpers.getFloatField(profile, "iconSize");
                    XposedHelpers.setFloatField(profile, "iconSize", currentIconSize * factor);
                } catch (Throwable ignored) {}
                try {
                    float currentAllAppsSize = XposedHelpers.getFloatField(profile, "allAppsIconSize");
                    XposedHelpers.setFloatField(profile, "allAppsIconSize", currentAllAppsSize * factor);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            report("applyGridAndScale", t);
        }
    }

    private static void hookBubbleTextView(ClassLoader loader) {
        try {
            Class<?> bubbleClass = XposedHelpers.findClassIfExists("com.android.launcher3.BubbleTextView", loader);
            if (bubbleClass == null) {
                XposedBridge.log(TAG + ": BubbleTextView not found");
                return;
            }

            // Hook applyIconAndLabel to apply translucent glass effect
            XposedBridge.hookAllMethods(bubbleClass, "applyIconAndLabel", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!glassIconsEnabled && iconAlpha >= 250) return;
                        Object target = param.thisObject;
                        if (target instanceof View) {
                            applyGlassEffectToIcon((View) target);
                        }
                    } catch (Throwable t) {
                        report("applyIconAndLabel", t);
                    }
                }
            });

            // Hook draw / dispatchDraw to maintain translucent alpha
            XposedBridge.hookAllMethods(bubbleClass, "setIcon", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!glassIconsEnabled && iconAlpha >= 250) return;
                        Object target = param.thisObject;
                        if (target instanceof View) {
                            applyGlassEffectToIcon((View) target);
                        }
                    } catch (Throwable t) {
                        report("setIcon", t);
                    }
                }
            });
        } catch (Throwable t) {
            report("hookBubbleTextView", t);
        }
    }

    private static void applyGlassEffectToIcon(View view) {
        try {
            Drawable iconDrawable = (Drawable) XposedHelpers.callMethod(view, "getIcon");
            if (iconDrawable != null) {
                if (glassIconsEnabled) {
                    // Subtle translucent glass styling with 75% alpha and slight desaturation
                    iconDrawable.setAlpha(Math.min(iconAlpha, 200));
                    // Frosted glass color filter matrix
                    ColorMatrix matrix = new ColorMatrix(new float[]{
                            0.95f, 0.05f, 0.05f, 0, 15,
                            0.05f, 0.95f, 0.05f, 0, 15,
                            0.05f, 0.05f, 0.95f, 0, 15,
                            0,     0,     0,     0.85f, 0
                    });
                    iconDrawable.setColorFilter(new ColorMatrixColorFilter(matrix));
                } else if (iconAlpha < 255) {
                    iconDrawable.setAlpha(iconAlpha);
                    iconDrawable.setColorFilter(null);
                }
            }
        } catch (Throwable t) {
            report("applyGlassEffectToIcon", t);
        }
    }

    private static synchronized void report(String stage, Throwable error) {
        if (REPORTED.add(stage)) {
            XposedBridge.log(TAG + ": " + stage + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
    }
}
