package io.github.wiojelt.dotsuite.systemui;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * Advanced Power Menu hook for Nothing OS / Asteroids.
 * Adds Recovery and Bootloader reboot options into GlobalActions power menu.
 */
final class AdvancedPowerMenuHook {
    private static final String TAG = "DotSuite.PowerMenu";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        XposedBridge.log(TAG + ": install() called");

        try {
            Class<?> dialogLiteClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.globalactions.GlobalActionsDialogLite", loader);
            final Class<?> restartActionClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.globalactions.GlobalActionsDialogLite$RestartAction", loader);

            if (dialogLiteClass == null || restartActionClass == null) {
                XposedBridge.log(TAG + ": GlobalActionsDialogLite or RestartAction not found");
                return;
            }

            XposedBridge.hookAllMethods(dialogLiteClass, "createActionItems", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        if (context == null) return;

                        boolean enabled = Settings.Secure.getInt(
                                context.getContentResolver(), PersonalizationPolicy.ADVANCED_POWER_MENU, 0) == 1;
                        if (!enabled) return;

                        Object itemsObj = XposedHelpers.getObjectField(param.thisObject, "mItems");
                        if (!(itemsObj instanceof ArrayList)) return;
                        @SuppressWarnings("unchecked")
                        ArrayList<Object> items = (ArrayList<Object>) itemsObj;

                        // Add Recovery Action
                        Object recoveryAction = XposedHelpers.newInstance(restartActionClass, param.thisObject);
                        XposedHelpers.setIntField(recoveryAction, "mMessageResId", 0);
                        XposedHelpers.setObjectField(recoveryAction, "mMessage", "Kurtarma (Recovery)");
                        items.add(recoveryAction);

                        // Add Bootloader Action
                        Object bootloaderAction = XposedHelpers.newInstance(restartActionClass, param.thisObject);
                        XposedHelpers.setIntField(bootloaderAction, "mMessageResId", 0);
                        XposedHelpers.setObjectField(bootloaderAction, "mMessage", "Önyükleyici (Fastboot)");
                        items.add(bootloaderAction);

                    } catch (Throwable t) {
                        report("createActionItems hook", t);
                    }
                }
            });

            XposedBridge.hookAllMethods(restartActionClass, "onPress", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object messageObj = XposedHelpers.getObjectField(param.thisObject, "mMessage");
                        String message = messageObj != null ? messageObj.toString() : "";

                        if ("Kurtarma (Recovery)".equals(message) || "Önyükleyici (Fastboot)".equals(message)) {
                            param.setResult(null); // Intercept stock restart

                            Object dialogLite = XposedHelpers.getObjectField(param.thisObject, "this$0");
                            try {
                                XposedHelpers.callMethod(dialogLite, "dismissDialog");
                            } catch (Throwable ignored) {}

                            final String target = "Kurtarma (Recovery)".equals(message) ? "recovery" : "bootloader";
                            XposedBridge.log(TAG + ": User selected advanced reboot: " + target);

                            // Execute reboot via IPowerManager
                            try {
                                Class<?> smClass = Class.forName("android.os.ServiceManager");
                                IBinder binder = (IBinder) smClass.getMethod("getService", String.class).invoke(null, Context.POWER_SERVICE);
                                if (binder != null) {
                                    Class<?> stubClass = Class.forName("android.os.IPowerManager$Stub");
                                    Object pm = smClass.getMethod("asInterface", IBinder.class).invoke(null, binder);
                                    if (pm != null) {
                                        XposedHelpers.callMethod(pm, "reboot", false, target, false);
                                        return;
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": IPowerManager direct reboot fallback: " + t.getMessage());
                            }

                            // Fallback via shell su command
                            try {
                                Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot " + target});
                            } catch (Throwable t) {
                                report("shell reboot execution", t);
                            }
                        }
                    } catch (Throwable t) {
                        report("onPress hook", t);
                    }
                }
            });
            XposedBridge.log(TAG + ": Advanced power menu hooked successfully");
        } catch (Throwable t) {
            report("install", t);
        }
    }

    private static synchronized void report(String message, Throwable error) {
        if (REPORTED.add(message)) {
            XposedBridge.log(TAG + ": " + message + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
    }
}
