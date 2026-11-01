package io.github.wiojelt.dotsuite.systemui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * Screen Transitions & LockStar Polish Hook for Nothing OS 3.0 / Asteroids.
 * Features:
 * 1. Screen-off animation selection (CRT Old TV, Smooth Fade, Scale Down).
 * 2. Lock screen charging wattage, voltage & current stats monitor.
 */
final class ScreenAnimationHook {
    private static final String TAG = "DotSuite.ScreenAnim";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;
    private static ContentResolver contentResolver;

    private static volatile int screenOffAnimStyle = 0; // 0: Stock, 1: CRT TV, 2: Smooth Fade, 3: Scale
    private static volatile boolean chargingInfoEnabled = false;

    // Battery / charging tracking
    private static volatile int batteryVoltage = 0;
    private static volatile int batteryCurrentMa = 0;
    private static volatile float batteryWattage = 0f;

    static synchronized void installSystemUi(ClassLoader loader) {
        if (installed) return;
        installed = true;
        XposedBridge.log(TAG + ": installSystemUi() called");

        hookKeyguardIndication(loader);
    }

    static synchronized void installSystemServer(ClassLoader loader) {
        XposedBridge.log(TAG + ": installSystemServer() called");
        hookDisplayPowerController(loader);
    }

    private static void hookKeyguardIndication(ClassLoader loader) {
        try {
            Class<?> indicationControllerClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.KeyguardIndicationController", loader);
            if (indicationControllerClass == null) {
                XposedBridge.log(TAG + ": KeyguardIndicationController not found");
                return;
            }

            XposedBridge.hookAllConstructors(indicationControllerClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        initObserver(context);
                    } catch (Throwable t) {
                        report("KeyguardIndicationController ctor", t);
                    }
                }
            });

            // Intercept computePowerIndication or updateIndication
            XposedBridge.hookAllMethods(indicationControllerClass, "computePowerIndication", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!chargingInfoEnabled || batteryWattage <= 0.1f) return;
                        CharSequence original = (CharSequence) param.getResult();
                        if (original != null && original.length() > 0) {
                            String extra = String.format(Locale.US, " (%.1fW · %dmA)", batteryWattage, batteryCurrentMa);
                            param.setResult(original + extra);
                        }
                    } catch (Throwable t) {
                        report("computePowerIndication hook", t);
                    }
                }
            });

        } catch (Throwable t) {
            report("hookKeyguardIndication", t);
        }
    }

    private static void hookDisplayPowerController(ClassLoader loader) {
        try {
            Class<?> dpcClass = XposedHelpers.findClassIfExists(
                    "com.android.server.display.DisplayPowerController", loader);
            if (dpcClass == null) return;

            // Hook initialize or setScreenOffAnimation
            XposedBridge.hookAllMethods(dpcClass, "animateScreenStateChange", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // param.args[0] target display state (e.g. Display.STATE_OFF)
                        if (screenOffAnimStyle == 1) { // CRT TV animation style
                            // Sets COLOR_FADE_STYLE_ELECTRON_BEAM / WARM_UP
                            try {
                                Object colorFade = XposedHelpers.getObjectField(param.thisObject, "mColorFadeOnAnimator");
                                if (colorFade != null) {
                                    XposedHelpers.setIntField(colorFade, "mMode", 1); // Electron beam / CRT
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) {
                        report("animateScreenStateChange", t);
                    }
                }
            });
        } catch (Throwable t) {
            report("hookDisplayPowerController", t);
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
                    Settings.Secure.getUriFor(PersonalizationPolicy.SCREEN_OFF_ANIM), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.LOCKSCREEN_CHARGING_INFO), false, observer);

            // Register battery monitor
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    updateBattery(ctx, intent);
                }
            }, filter);

            XposedBridge.log(TAG + ": ScreenAnimation observers registered");
        } catch (Throwable t) {
            report("initObserver", t);
        }
    }

    private static void updateSettings() {
        if (contentResolver == null) return;
        try {
            screenOffAnimStyle = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.SCREEN_OFF_ANIM, 0);
            chargingInfoEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.LOCKSCREEN_CHARGING_INFO, 0) == 1;
        } catch (Throwable t) {
            report("updateSettings", t);
        }
    }

    private static void updateBattery(Context context, Intent intent) {
        if (intent == null) return;
        try {
            int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            batteryVoltage = voltageMv > 1000 ? voltageMv / 1000 : voltageMv;

            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (currentUa != Integer.MIN_VALUE && currentUa != 0) {
                    batteryCurrentMa = Math.abs(currentUa) / 1000;
                    if (batteryVoltage > 0 && batteryCurrentMa > 0) {
                        batteryWattage = (batteryVoltage * batteryCurrentMa) / 1000.0f;
                    }
                }
            }
        } catch (Throwable t) {
            report("updateBattery", t);
        }
    }

    private static synchronized void report(String stage, Throwable error) {
        if (REPORTED.add(stage)) {
            XposedBridge.log(TAG + ": " + stage + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
    }
}
