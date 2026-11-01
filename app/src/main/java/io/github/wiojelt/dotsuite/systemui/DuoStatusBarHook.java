package io.github.wiojelt.dotsuite.systemui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * Duo Status Bar (DuoSB) & Status Interaction hook for Nothing OS 3.0 / Asteroids.
 * Features:
 * 1. Duo Pill: Unified Nothing-style pill capsule combining battery percentage and network traffic.
 * 2. Double-tap status bar to sleep.
 * 3. Status bar brightness slider gesture (swipe horizontally across top bar to adjust brightness).
 * 4. Network Traffic Speed Monitor.
 * 5. Status bar padding adjustments (start & end dp).
 * 6. Status bar clock seconds & weekday display.
 */
@SuppressLint("NewApi")
final class DuoStatusBarHook {
    private static final String TAG = "DotSuite.DuoSB";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;
    private static ContentResolver contentResolver;

    private static volatile boolean duoEnabled = false;
    private static volatile boolean batteryPercentEnabled = true;
    private static volatile boolean doubleTapSleepEnabled = false;
    private static volatile boolean trafficMonitorEnabled = false;
    private static volatile boolean brightnessGestureEnabled = false;
    private static volatile int paddingStartDp = 0;
    private static volatile int paddingEndDp = 0;
    private static volatile boolean clockSecondsEnabled = false;
    private static volatile boolean clockDayEnabled = false;

    // View references
    private static LinearLayout duoPillView;
    private static TextView batteryText;
    private static TextView trafficText;
    private static Handler uiHandler;
    private static long lastRxBytes = 0;
    private static long lastTxBytes = 0;
    private static long lastTrafficTime = 0;

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        XposedBridge.log(TAG + ": install() called");

        uiHandler = new Handler(Looper.getMainLooper());
        hookStatusBarView(loader);
        hookStatusBarClock(loader);
    }

    private static void hookStatusBarView(ClassLoader loader) {
        try {
            Class<?> statusBarViewClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.PhoneStatusBarView", loader);
            if (statusBarViewClass == null) {
                XposedBridge.log(TAG + ": PhoneStatusBarView not found");
                return;
            }

            XposedBridge.hookAllMethods(statusBarViewClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View statusBar = (View) param.thisObject;
                        initObserver(statusBar.getContext());
                        setupDuoPill(statusBar);
                        applyPadding(statusBar);
                    } catch (Throwable t) {
                        report("onFinishInflate hook", t);
                    }
                }
            });

            XposedBridge.hookAllMethods(statusBarViewClass, "onLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View statusBar = (View) param.thisObject;
                        applyPadding(statusBar);
                    } catch (Throwable ignored) {}
                }
            });

            // Gestures: Double-tap to sleep and Brightness Slider on PhoneStatusBarView
            XposedBridge.hookAllMethods(statusBarViewClass, "onTouchEvent", new XC_MethodHook() {
                private long lastTapTime = 0;
                private float initialTouchX = 0f;
                private float initialTouchY = 0f;
                private int initialBrightness = 128;
                private boolean isSlidingBrightness = false;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        MotionEvent event = (MotionEvent) param.args[0];
                        if (event == null) return;
                        View view = (View) param.thisObject;
                        int action = event.getActionMasked();

                        if (action == MotionEvent.ACTION_DOWN) {
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isSlidingBrightness = false;
                            if (brightnessGestureEnabled) {
                                initialBrightness = Settings.System.getInt(
                                        view.getContext().getContentResolver(),
                                        Settings.System.SCREEN_BRIGHTNESS, 128);
                            }
                        } else if (action == MotionEvent.ACTION_MOVE) {
                            if (brightnessGestureEnabled) {
                                float deltaX = event.getRawX() - initialTouchX;
                                float deltaY = Math.abs(event.getRawY() - initialTouchY);
                                if (!isSlidingBrightness && Math.abs(deltaX) > 30 && deltaY < 40) {
                                    isSlidingBrightness = true;
                                }
                                if (isSlidingBrightness) {
                                    int screenWidth = view.getResources().getDisplayMetrics().widthPixels;
                                    float fraction = deltaX / (float) screenWidth;
                                    int newBrightness = (int) Math.max(5, Math.min(255, initialBrightness + fraction * 255));
                                    Settings.System.putInt(
                                            view.getContext().getContentResolver(),
                                            Settings.System.SCREEN_BRIGHTNESS, newBrightness);
                                    param.setResult(true);
                                    return;
                                }
                            }
                        } else if (action == MotionEvent.ACTION_UP) {
                            if (isSlidingBrightness) {
                                isSlidingBrightness = false;
                                param.setResult(true);
                                return;
                            }
                            if (doubleTapSleepEnabled) {
                                long now = SystemClock.uptimeMillis();
                                if (now - lastTapTime < 350) {
                                    PowerManager pm = (PowerManager) view.getContext().getSystemService(Context.POWER_SERVICE);
                                    if (pm != null) {
                                        XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
                                        param.setResult(true);
                                    }
                                }
                                lastTapTime = now;
                            }
                        }
                    } catch (Throwable t) {
                        report("onTouchEvent hook", t);
                    }
                }
            });

        } catch (Throwable t) {
            report("hookStatusBarView", t);
        }
    }

    private static void hookStatusBarClock(ClassLoader loader) {
        Class<?> clockClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.policy.Clock", loader);
        if (clockClass == null) return;

        try {
            XposedBridge.hookAllMethods(clockClass, "updateClock", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!clockSecondsEnabled && !clockDayEnabled) return;
                        TextView clockView = (TextView) param.thisObject;
                        SimpleDateFormat format;
                        if (clockDayEnabled && clockSecondsEnabled) {
                            format = new SimpleDateFormat("EEE HH:mm:ss", Locale.getDefault());
                        } else if (clockDayEnabled) {
                            format = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());
                        } else {
                            format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                        }
                        clockView.setText(format.format(new Date()));
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + ": Clock seconds/day hooked");
        } catch (Throwable t) {
            report("hookStatusBarClock", t);
        }
    }

    private static void applyPadding(View statusBar) {
        if (statusBar == null) return;
        if (paddingStartDp > 0 || paddingEndDp > 0) {
            Context ctx = statusBar.getContext();
            int start = paddingStartDp > 0 ? dp(ctx, paddingStartDp) : statusBar.getPaddingStart();
            int end = paddingEndDp > 0 ? dp(ctx, paddingEndDp) : statusBar.getPaddingEnd();
            statusBar.setPadding(start, statusBar.getPaddingTop(), end, statusBar.getPaddingBottom());
        }
    }

    static void initObserver(Context context) {
        if (contentResolver != null || context == null) return;
        try {
            contentResolver = context.getApplicationContext().getContentResolver();
            updateSettings();

            ContentObserver observer = new ContentObserver(uiHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    updateSettings();
                }
            };

            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.DUO_STATUSBAR), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.DUO_BATTERY_PERCENT), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.STATUS_DOUBLE_SLEEP), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.NETWORK_TRAFFIC), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.STATUS_BAR_BRIGHTNESS), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.STATUS_PADDING_START), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.STATUS_PADDING_END), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.CLOCK_SECONDS), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.CLOCK_DAY), false, observer);

            // Register battery status receiver
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    updateBatteryFromIntent(intent);
                }
            }, filter);

            startTrafficLoop();
            XposedBridge.log(TAG + ": ContentObservers and receivers registered");
        } catch (Throwable t) {
            report("initObserver", t);
        }
    }

    private static void updateSettings() {
        if (contentResolver == null) return;
        try {
            duoEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.DUO_STATUSBAR, 0) == 1;
            batteryPercentEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.DUO_BATTERY_PERCENT, 1) == 1;
            doubleTapSleepEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.STATUS_DOUBLE_SLEEP, 0) == 1;
            trafficMonitorEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.NETWORK_TRAFFIC, 0) == 1;
            brightnessGestureEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.STATUS_BAR_BRIGHTNESS, 0) == 1;
            paddingStartDp = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.STATUS_PADDING_START, 0);
            paddingEndDp = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.STATUS_PADDING_END, 0);
            clockSecondsEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.CLOCK_SECONDS, 0) == 1;
            clockDayEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.CLOCK_DAY, 0) == 1;

            if (duoPillView != null) {
                duoPillView.setVisibility(duoEnabled ? View.VISIBLE : View.GONE);
            }
            if (trafficText != null) {
                trafficText.setVisibility(trafficMonitorEnabled ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable t) {
            report("updateSettings", t);
        }
    }

    @SuppressLint("SetTextI18n")
    private static void setupDuoPill(View statusBar) {
        if (!(statusBar instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) statusBar;
        Context context = statusBar.getContext();

        try {
            int sysIconsId = context.getResources().getIdentifier("system_icons", "id", "com.android.systemui");
            View sysIcons = sysIconsId != 0 ? root.findViewById(sysIconsId) : null;

            duoPillView = new LinearLayout(context);
            duoPillView.setOrientation(LinearLayout.HORIZONTAL);
            duoPillView.setGravity(Gravity.CENTER_VERTICAL);
            duoPillView.setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2));

            // Translucent glass capsule background
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(context, 14));
            bg.setColor(0x28FFFFFF); // Frosted subtle glass
            bg.setStroke(dp(context, 1), 0x3DFFFFFF);
            duoPillView.setBackground(bg);

            // Traffic text
            trafficText = new TextView(context);
            trafficText.setTextColor(Color.WHITE);
            trafficText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            trafficText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            trafficText.setText("0 K/s");
            trafficText.setVisibility(trafficMonitorEnabled ? View.VISIBLE : View.GONE);
            trafficText.setPadding(0, 0, dp(context, 6), 0);
            duoPillView.addView(trafficText);

            // Battery percentage text
            batteryText = new TextView(context);
            batteryText.setTextColor(Color.WHITE);
            batteryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            batteryText.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            batteryText.setText("100%");
            duoPillView.addView(batteryText);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 22));
            lp.setMargins(dp(context, 4), 0, dp(context, 4), 0);
            duoPillView.setLayoutParams(lp);

            duoPillView.setVisibility(duoEnabled ? View.VISIBLE : View.GONE);

            if (sysIcons instanceof ViewGroup) {
                ((ViewGroup) sysIcons).addView(duoPillView, 0);
            } else {
                root.addView(duoPillView);
            }
            XposedBridge.log(TAG + ": setupDuoPill successfully added capsule");
        } catch (Throwable t) {
            report("setupDuoPill", t);
        }
    }

    private static void updateBatteryFromIntent(Intent intent) {
        if (intent == null) return;
        try {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            if (level >= 0 && scale > 0) {
                int percent = (int) ((level / (float) scale) * 100);
                uiHandler.post(() -> {
                    if (batteryText != null) {
                        batteryText.setText(percent + "%" + (isCharging ? " ⚡" : ""));
                    }
                });
            }
        } catch (Throwable t) {
            report("updateBatteryFromIntent", t);
        }
    }

    private static void startTrafficLoop() {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (trafficMonitorEnabled && trafficText != null && duoEnabled) {
                        long currentRx = TrafficStats.getTotalRxBytes();
                        long currentTx = TrafficStats.getTotalTxBytes();
                        long now = SystemClock.elapsedRealtime();

                        if (lastTrafficTime > 0 && now > lastTrafficTime) {
                            long rxDiff = currentRx - lastRxBytes;
                            long txDiff = currentTx - lastTxBytes;
                            long durationSec = Math.max(1, (now - lastTrafficTime) / 1000);
                            long speedBps = (rxDiff + txDiff) / durationSec;

                            String speedStr;
                            if (speedBps > 1024 * 1024) {
                                speedStr = String.format("%.1f M/s", speedBps / (1024.0f * 1024.0f));
                            } else {
                                speedStr = String.format("%d K/s", speedBps / 1024);
                            }
                            trafficText.setText(speedStr);
                        }
                        lastRxBytes = currentRx;
                        lastTxBytes = currentTx;
                        lastTrafficTime = now;
                    }
                } catch (Throwable ignored) {}
                uiHandler.postDelayed(this, 1500);
            }
        }, 1500);
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    private static synchronized void report(String stage, Throwable error) {
        if (REPORTED.add(stage)) {
            XposedBridge.log(TAG + ": " + stage + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
    }
}
