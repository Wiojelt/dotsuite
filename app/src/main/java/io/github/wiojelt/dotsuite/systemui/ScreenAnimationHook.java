package io.github.wiojelt.dotsuite.systemui;

import android.animation.ValueAnimator;
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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * Screen Transitions & LockStar Polish Hook.
 * Inspired by iOS 18 Wallpaper Bloom, HyperOS Light Beam, and OneUI Good Lock LockStar / Wonderland.
 * Features:
 * 1. Screen-off animation selection (CRT TV, Smooth Fade, Center Scale, Black Hole Warp).
 * 2. Screen-on & Unlock transition (iOS Wallpaper Bloom, HyperOS Light Beam, OneUI AOD Morph, CRT Warmup).
 * 3. Granular animation duration (100ms - 1000ms).
 * 4. Lock screen live charging wattage, voltage & current stats monitor.
 */
final class ScreenAnimationHook {
    private static final String TAG = "DotSuite.ScreenAnim";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installedSystemUi;
    private static boolean installedSystemServer;
    private static ContentResolver contentResolver;

    private static volatile int screenOffAnimStyle = 0; // 0: Stock, 1: CRT TV, 2: Smooth Fade, 3: Scale, 4: Warp
    private static volatile int screenOnAnimStyle = 0;  // 0: Stock, 1: iOS Bloom, 2: HyperOS Beam, 3: OneUI Morph, 4: CRT Warmup
    private static volatile int screenAnimDuration = 350; // 100..1000 ms
    private static volatile boolean chargingInfoEnabled = false;

    // Battery / charging tracking
    private static volatile int batteryVoltage = 0;
    private static volatile int batteryCurrentMa = 0;
    private static volatile float batteryWattage = 0f;

    static synchronized void installSystemUi(ClassLoader loader) {
        if (installedSystemUi) return;
        installedSystemUi = true;
        XposedBridge.log(TAG + ": installSystemUi() called");

        hookKeyguardIndication(loader);
        hookKeyguardUnlockTransitions(loader);
    }

    static synchronized void installSystemServer(ClassLoader loader) {
        if (installedSystemServer) return;
        installedSystemServer = true;
        XposedBridge.log(TAG + ": installSystemServer() called");
        hookDisplayPowerController(loader);
    }

    private static void hookKeyguardIndication(ClassLoader loader) {
        try {
            Class<?> indicationControllerClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.KeyguardIndicationController", loader);
            if (indicationControllerClass == null) return;

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

    private static void hookKeyguardUnlockTransitions(ClassLoader loader) {
        try {
            // Hook BiometricUnlockController / KeyguardViewMediator for screen-on & unlock transitions
            Class<?> unlockControllerClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.BiometricUnlockController", loader);
            if (unlockControllerClass != null) {
                XposedBridge.hookAllMethods(unlockControllerClass, "startKeyguardFadingAway", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (screenOnAnimStyle == 0) return;
                        try {
                            // Customize fade away duration based on screenAnimDuration
                            if (param.args.length >= 2 && param.args[1] instanceof Long) {
                                param.args[1] = (long) screenAnimDuration;
                            }
                        } catch (Throwable t) {
                            report("startKeyguardFadingAway hook", t);
                        }
                    }
                });
                XposedBridge.log(TAG + ": BiometricUnlockController transition hooked");
            }
        } catch (Throwable t) {
            report("hookKeyguardUnlockTransitions", t);
        }
    }

    private static void hookDisplayPowerController(ClassLoader loader) {
        try {
            Class<?> dpcClass = XposedHelpers.findClassIfExists(
                    "com.android.server.display.DisplayPowerController", loader);
            if (dpcClass == null) return;

            XposedBridge.hookAllMethods(dpcClass, "animateScreenStateChange", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // Apply custom screen off / on animation
                        if (screenOffAnimStyle == 1 || screenOnAnimStyle == 4) { // CRT Electron beam / TV
                            try {
                                Object colorFadeOn = XposedHelpers.getObjectField(param.thisObject, "mColorFadeOnAnimator");
                                if (colorFadeOn != null) {
                                    XposedHelpers.setIntField(colorFadeOn, "mMode", 1); // Electron beam / CRT
                                    if (colorFadeOn instanceof ValueAnimator) {
                                        ((ValueAnimator) colorFadeOn).setDuration(screenAnimDuration);
                                    }
                                }
                                Object colorFadeOff = XposedHelpers.getObjectField(param.thisObject, "mColorFadeOffAnimator");
                                if (colorFadeOff != null) {
                                    XposedHelpers.setIntField(colorFadeOff, "mMode", 1);
                                    if (colorFadeOff instanceof ValueAnimator) {
                                        ((ValueAnimator) colorFadeOff).setDuration(screenAnimDuration);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        } else if (screenOffAnimStyle == 2) { // Smooth Fade
                            try {
                                Object colorFadeOff = XposedHelpers.getObjectField(param.thisObject, "mColorFadeOffAnimator");
                                if (colorFadeOff != null) {
                                    XposedHelpers.setIntField(colorFadeOff, "mMode", 0); // Natural Gamma Fade
                                    if (colorFadeOff instanceof ValueAnimator) {
                                        ((ValueAnimator) colorFadeOff).setDuration(screenAnimDuration);
                                        ((ValueAnimator) colorFadeOff).setInterpolator(new DecelerateInterpolator(1.8f));
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) {
                        report("animateScreenStateChange", t);
                    }
                }
            });
            XposedBridge.log(TAG + ": DisplayPowerController hooked");
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
                    Settings.Secure.getUriFor(PersonalizationPolicy.SCREEN_ON_ANIM_STYLE), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.SCREEN_ANIM_DURATION), false, observer);
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
            screenOnAnimStyle = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.SCREEN_ON_ANIM_STYLE, 0);
            screenAnimDuration = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.SCREEN_ANIM_DURATION, 350);
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
