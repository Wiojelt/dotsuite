package io.github.wiojelt.dotsuite.systemui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * Battery Protection (Charging Cap) hook for Nothing OS / Asteroids.
 * Enforces charge threshold (e.g. 80%) using Nothing's vendor.noth.hardware.charge.ICharge HAL
 * and kernel sysfs nodes when plugged in.
 */
final class BatteryCapHook {
    private static final String TAG = "DotSuite.BatteryCap";
    private static final Set<String> REPORTED = new HashSet<>();
    private static final String ICHARGE_SERVICE_NAME = "vendor.noth.hardware.charge.ICharge/default";
    private static final String NOTIF_CHANNEL_ID = "dotsuite_battery_cap_channel";
    private static final int NOTIF_ID = 8081;

    private static boolean installed;
    private static Context appContext;
    private static Object iChargeInstance;
    private static boolean iChargeResolved;
    private static volatile boolean isCapped;
    private static volatile int lastCapLevel = -1;

    private static final String[] SYSFS_CHARGING_PATHS = {
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/class/power_supply/battery/battery_charging_enabled",
            "/sys/class/qcom-battery/charging_enabled"
    };

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        XposedBridge.log(TAG + ": install() called");

        hookBatteryService(loader);
    }

    private static void hookBatteryService(final ClassLoader loader) {
        try {
            Class<?> batteryServiceClass = XposedHelpers.findClass(
                    "com.android.server.BatteryService", loader);

            XposedBridge.hookAllConstructors(batteryServiceClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] instanceof Context) {
                        appContext = (Context) param.args[0];
                        registerReceiver(appContext, loader);
                    }
                }
            });

            XposedBridge.hookAllMethods(batteryServiceClass, "processValuesLocked", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (appContext == null) {
                        try {
                            appContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                            if (appContext != null) {
                                registerReceiver(appContext, loader);
                            }
                        } catch (Throwable ignored) {}
                    }
                    enforceBatteryCap(param.thisObject, loader);
                }
            });

            XposedBridge.hookAllMethods(batteryServiceClass, "onBootPhase", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && Integer.valueOf(1000).equals(param.args[0])) {
                        if (!isCapped) {
                            setHardwareChargingEnabled(true, loader);
                        }
                    }
                }
            });
            XposedBridge.log(TAG + ": BatteryService hooked successfully");
        } catch (Throwable t) {
            report("hookBatteryService", t);
        }
    }

    private static void enforceBatteryCap(Object batteryService, ClassLoader loader) {
        if (appContext == null) return;
        try {
            boolean enabled = Settings.Secure.getInt(
                    appContext.getContentResolver(), PersonalizationPolicy.BATTERY_CAP_ENABLED, 0) == 1;
            int targetLevel = Settings.Secure.getInt(
                    appContext.getContentResolver(), PersonalizationPolicy.BATTERY_CAP_LEVEL, 80);

            Object healthInfo = XposedHelpers.getObjectField(batteryService, "mHealthInfo");
            if (healthInfo == null) return;

            int batteryLevel = XposedHelpers.getIntField(healthInfo, "batteryLevel");
            int plugType = 0;
            try {
                Object plugObj = XposedHelpers.callStaticMethod(batteryService.getClass(), "plugType", healthInfo);
                if (plugObj instanceof Integer) {
                    plugType = (Integer) plugObj;
                } else {
                    plugType = XposedHelpers.getIntField(batteryService, "mPlugType");
                }
            } catch (Throwable t) {
                plugType = XposedHelpers.getIntField(batteryService, "mPlugType");
            }

            boolean isPlugged = plugType != 0;

            if (!enabled || !isPlugged) {
                if (isCapped) {
                    isCapped = false;
                    setHardwareChargingEnabled(true, loader);
                    updateNotification(false, targetLevel);
                }
                lastCapLevel = targetLevel;
                return;
            }

            if (lastCapLevel != -1 && targetLevel != lastCapLevel) {
                if (batteryLevel < targetLevel) {
                    isCapped = false;
                    setHardwareChargingEnabled(true, loader);
                    updateNotification(false, targetLevel);
                }
            }
            lastCapLevel = targetLevel;

            if (batteryLevel >= targetLevel) {
                if (!isCapped) {
                    isCapped = true;
                    XposedBridge.log(TAG + ": Battery reached cap (" + batteryLevel + "% >= " + targetLevel + "%). Pausing charge.");
                    setHardwareChargingEnabled(false, loader);
                    updateNotification(true, targetLevel);
                }
                // Set battery status to BATTERY_STATUS_NOT_CHARGING (4)
                XposedHelpers.setIntField(healthInfo, "batteryStatus", 4);
            } else {
                int resumeThreshold = targetLevel - 2;
                if (batteryLevel <= resumeThreshold) {
                    if (isCapped) {
                        isCapped = false;
                        XposedBridge.log(TAG + ": Battery dropped to " + batteryLevel + "%. Resuming charging.");
                        setHardwareChargingEnabled(true, loader);
                        updateNotification(false, targetLevel);
                    }
                } else {
                    if (isCapped) {
                        setHardwareChargingEnabled(false, loader);
                        XposedHelpers.setIntField(healthInfo, "batteryStatus", 4);
                    }
                }
            }
        } catch (Throwable t) {
            report("enforceBatteryCap", t);
        }
    }

    private static synchronized void setHardwareChargingEnabled(boolean enable, ClassLoader loader) {
        Object iCharge = getICharge(loader);
        if (iCharge != null) {
            try {
                XposedHelpers.callMethod(iCharge, "setUsb_charger_en", enable ? 1 : 0);
                XposedBridge.log(TAG + ": ICharge#setUsb_charger_en(" + (enable ? 1 : 0) + ")");
            } catch (Throwable t) {
                report("ICharge#setUsb_charger_en", t);
            }
        }

        String stateStr = enable ? "1" : "0";
        for (String path : SYSFS_CHARGING_PATHS) {
            try {
                File file = new File(path);
                if (file.exists() && file.canWrite()) {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(stateStr.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static Object getICharge(ClassLoader loader) {
        if (iChargeResolved) return iChargeInstance;
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) smClass.getMethod("getService", String.class)
                    .invoke(null, ICHARGE_SERVICE_NAME);
            if (binder != null) {
                Class<?> stubClass = XposedHelpers.findClassIfExists(
                        "vendor.noth.hardware.charge.ICharge$Stub", loader);
                if (stubClass != null) {
                    iChargeInstance = XposedHelpers.callStaticMethod(stubClass, "asInterface", binder);
                    iChargeResolved = true;
                    XposedBridge.log(TAG + ": Connected to Nothing ICharge HAL");
                }
            }
        } catch (Throwable t) {
            report("bind ICharge HAL", t);
        }
        return iChargeInstance;
    }

    private static void registerReceiver(Context context, final ClassLoader loader) {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction("io.github.wiojelt.dotsuite.BATTERY_CAP_CHANGED");
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    XposedBridge.log(TAG + ": BATTERY_CAP_CHANGED broadcast received");
                    enforceBatteryCap(null, loader);
                }
            }, filter, Context.RECEIVER_EXPORTED);
            XposedBridge.log(TAG + ": Registered dynamic BATTERY_CAP_CHANGED receiver");
        } catch (Throwable t) {
            report("registerReceiver", t);
        }
    }

    private static void updateNotification(boolean active, int targetLevel) {
        if (appContext == null) return;
        try {
            NotificationManager nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (!active) {
                nm.cancel(NOTIF_ID);
                return;
            }

            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID, "Battery Protection", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows when charging is paused at user set limit");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);

            Notification notif = new Notification.Builder(appContext, NOTIF_CHANNEL_ID)
                    .setContentTitle("Charging paused at " + targetLevel + "%")
                    .setContentText("Nothing battery protection is active to preserve battery health.")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setShowWhen(false)
                    .build();

            nm.notify(NOTIF_ID, notif);
        } catch (Throwable t) {
            report("updateNotification", t);
        }
    }

    private static synchronized void report(String message, Throwable error) {
        if (REPORTED.add(message)) {
            XposedBridge.log(TAG + ": " + message + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
    }
}
