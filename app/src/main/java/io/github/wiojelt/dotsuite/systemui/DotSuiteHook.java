package io.github.wiojelt.dotsuite.systemui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Puts DotSuite's per-player behavior into Nothing OS's real volume dialog.
 *
 * No replacement panel is drawn. Every added row is inflated from SystemUI's own
 * {@code volume_dialog_row} resource, including Nothing's dot line, seekbar drawable, dimensions,
 * background, icon target and expansion animation. The only structural addition is an invisible
 * HorizontalScrollView around SystemUI's existing row strip so stock and app rows remain reachable.
 */
@SuppressLint("NewApi") // The entry point hard-gates this class to this Android 16 device.
public final class DotSuiteHook implements IXposedHookLoadPackage {
    private static final int MAX_VISIBLE_COLUMNS = 4;
    private static final String SYSTEM_SERVER = "android";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String PHONE_WINDOW_MANAGER =
            "com.android.server.policy.PhoneWindowManager";
    private static final String DIALOG_CLASS = "com.android.systemui.volume.VolumeDialogImpl";
    private static final String TAG = "DotSuite";
    private static final String BUILD_ID = io.github.wiojelt.dotsuite.BuildConfig.VERSION_NAME;
    private static final String KILL_SWITCH = "dotsuite_systemui_enabled";
    private static final String MEDIA_KEYS_SWITCH = "dotsuite_screen_off_keys_enabled";
    private static final String PANEL_SIDE = "dotsuite_panel_side";
    private static final String SHOW_CAPTIONS = "dotsuite_show_captions";
    private static final String SHOW_SETTINGS = "dotsuite_show_settings";
    private static final String PANEL_TIMEOUT_MS = "dotsuite_panel_timeout_ms";
    private static final String AUTO_EXPAND = "dotsuite_auto_expand";
    private static final String VOLUME_STEP_PERCENT = "dotsuite_volume_step_percent";
    private static final String ALWAYS_MEDIA_VOLUME = "dotsuite_always_media_volume";
    private static final String SCRAMBLE_PIN = "dotsuite_scramble_pin";
    private static final String HIDE_PIN_INPUT = "dotsuite_hide_pin_input";
    private static final String MATERIAL_PIN_KEYS = "dotsuite_material_pin_keys";
    private static final int SIDE_AUTO = 0;
    private static final int SIDE_LEFT = 1;
    private static final int SIDE_RIGHT = 2;
    private static final long LONG_PRESS_MS = 550L;
    private static final long KEY_REPEAT_RATE_LIMIT_MS = 180L;
    private static final Map<Object, DialogController> CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, ScreenOffMediaKeyController> MEDIA_KEY_CONTROLLERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static int loadSequence;
    private static boolean systemUiHookInstalled;
    private static boolean lockscreenHookInstalled;
    private static final SecureRandom PIN_RANDOM = new SecureRandom();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        boolean isSystemUi = SYSTEM_UI.equals(param.packageName)
                && SYSTEM_UI.equals(param.processName);
        boolean isSystemServer = SYSTEM_SERVER.equals(param.packageName)
                && SYSTEM_SERVER.equals(param.processName);
        boolean isResolver = "com.android.intentresolver".equals(param.packageName);
        if (!isSystemUi && !isSystemServer && !isResolver) return;
        if (Build.VERSION.SDK_INT != 36 || !"Asteroids".equals(Build.DEVICE)) {
            XposedBridge.log(TAG + ": unsupported device/API; stock panel kept");
            return;
        }

        if (isResolver) { ShareSheetHook.install(param.classLoader); return; }
        if (isSystemServer) {
            PowerTorchHook.install(param.classLoader);
            hookScreenOffMediaKeys(param.classLoader);
            return;
        }

        hookLockscreen(param.classLoader);
        hookNothingVolumePanel(param.classLoader);
        SystemInteractionHook.install(param.classLoader);
        NativeVisualHook.install(param.classLoader);
        BackArrowHook.install(param.classLoader);
    }

    private static synchronized void hookLockscreen(ClassLoader classLoader) {
        if (lockscreenHookInstalled) return;
        try {
            Class<?> pinViewClass = XposedHelpers.findClass(
                    "com.android.keyguard.KeyguardPINView", classLoader);
            Class<?> controllerClass = XposedHelpers.findClass(
                    "com.android.keyguard.KeyguardPinBasedInputViewController", classLoader);
            Class<?> passwordClass = XposedHelpers.findClass(
                    "com.android.keyguard.PasswordTextView", classLoader);

            XposedBridge.hookAllMethods(pinViewClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyPinFeatures(param.thisObject);
                }
            });
            XposedBridge.hookAllMethods(controllerClass, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object view = XposedHelpers.getObjectField(param.thisObject, "mView");
                        if (pinViewClass.isInstance(view)) applyPinFeatures(view);
                    } catch (Throwable error) {
                        XposedBridge.log(TAG + ": lockscreen resume skipped: " + error);
                    }
                }
            });
            XposedBridge.hookAllMethods(passwordClass, "setShowPassword", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View
                            && secureFlag((View) param.thisObject, HIDE_PIN_INPUT, 1)) {
                        param.args[0] = false;
                    }
                }
            });
            XposedBridge.hookAllConstructors(passwordClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View
                            && secureFlag((View) param.thisObject, HIDE_PIN_INPUT, 1)) {
                        XposedHelpers.callMethod(param.thisObject, "setShowPassword", false);
                    }
                }
            });
            lockscreenHookInstalled = true;
            XposedBridge.log(TAG + ": " + BUILD_ID + " lockscreen hook ready");
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": lockscreen hook unavailable; stock keypad kept: " + error);
        }
    }

    private static void applyPinFeatures(Object pinView) {
        if (!(pinView instanceof View)) return;
        View root = (View) pinView;
        try {
            List<Integer> digits = new ArrayList<>(10);
            for (int digit = 0; digit <= 9; digit++) digits.add(digit);
            boolean scramblePin = secureFlag(root, SCRAMBLE_PIN, 0);
            if (scramblePin) {
                Collections.shuffle(digits, PIN_RANDOM);
            }
            boolean materialKeys = secureFlag(root, MATERIAL_PIN_KEYS, 0);
            for (int position = 0; position <= 9; position++) {
                int id = root.getResources().getIdentifier(
                        "key" + position, "id", SYSTEM_UI);
                View key = id == 0 ? null : root.findViewById(id);
                if (key == null) continue;
                int digit = digits.get(position);
                XposedHelpers.setIntField(key, "mDigit", digit);
                Object digitText = XposedHelpers.getObjectField(key, "mDigitText");
                if (digitText instanceof TextView) {
                    ((TextView) digitText).setText(Integer.toString(digit));
                }
                key.setContentDescription(Integer.toString(digit));
                Object klondike = XposedHelpers.getObjectField(key, "mKlondikeText");
                if (klondike instanceof TextView) {
                    ((TextView) klondike).setVisibility(
                            materialKeys || scramblePin
                                    ? View.INVISIBLE : View.VISIBLE);
                }
                applyMaterialPinKey(key, materialKeys);
            }
            int entryId = root.getResources().getIdentifier("pinEntry", "id", SYSTEM_UI);
            View entry = entryId == 0 ? null : root.findViewById(entryId);
            if (entry != null) {
                XposedHelpers.callMethod(entry, "setShowPassword",
                        !secureFlag(root, HIDE_PIN_INPUT, 1));
            }
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": lockscreen styling skipped; stock keypad continues: " + error);
        }
    }

    private static void applyMaterialPinKey(View key, boolean enabled) {
        try {
            if (!enabled) {
                XposedHelpers.callMethod(key, "reloadColors");
                return;
            }
            Drawable background = key.getBackground();
            if (!(background instanceof GradientDrawable)) return;
            GradientDrawable shape = (GradientDrawable) background.mutate();
            int normalColor = Color.argb(38, 255, 255, 255);
            int pressedColor = Color.argb(76, 255, 255, 255);
            shape.setColor(normalColor);
            int stroke = Math.max(1, Math.round(key.getResources()
                    .getDisplayMetrics().density));
            shape.setStroke(stroke, Color.argb(48, 255, 255, 255));
            shape.setCornerRadius(10_000f);
            // NumPadAnimator owns this same drawable and otherwise restores the stock colours
            // after the first press. Updating only its colour cache keeps native press geometry,
            // haptics and timing while making the optional surface persistent.
            Object animator = XposedHelpers.getObjectField(key, "mAnimator");
            if (animator != null) {
                XposedHelpers.setIntField(animator, "mNormalBackgroundColor", normalColor);
                XposedHelpers.setIntField(animator, "mPressedBackgroundColor", pressedColor);
            }
        } catch (Throwable ignored) {
            // Nothing may replace its keypad drawable on a later build; keep the native style.
        }
    }

    private static boolean secureFlag(View view, String key, int fallback) {
        try {
            return Settings.Secure.getInt(
                    view.getContext().getContentResolver(), key, fallback) != 0;
        } catch (Throwable ignored) {
            return fallback != 0;
        }
    }

    private static synchronized void hookNothingVolumePanel(ClassLoader classLoader) {
        // Vector can deliver a second package-load callback with a restricted loader a few
        // seconds after the real SystemUI loader. The first successful installation is complete;
        // retrying only creates misleading ClassNotFound logs and risks duplicate callbacks.
        if (systemUiHookInstalled) return;
        try {
            final int loadId = ++loadSequence;
            Class<?> dialogClass = XposedHelpers.findClass(DIALOG_CLASS, classLoader);
            XposedBridge.log(TAG + ": " + BUILD_ID + " load #" + loadId + " classLoader=" + classLoader
                    + " dialogLoader=" + dialogClass.getClassLoader());
            XposedBridge.hookAllMethods(dialogClass, "initDialog", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, DialogController::prepareDialogInflation);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, DialogController::onDialogInflated);
                }
            });
            XposedBridge.hookAllMethods(dialogClass, "showH", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, DialogController::prepareForShow);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, controller -> {
                        controller.showing = true;
                        controller.refresh();
                        controller.onShown();
                    });
                }
            });
            XposedBridge.hookAllMethods(dialogClass, "updateRowsH", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, DialogController::refresh);
                }
            });
            XposedBridge.hookAllMethods(dialogClass, "dismissH", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, controller -> controller.showing = false);
                }
            });
            XposedBridge.hookAllMethods(dialogClass, "updateODICaptionsH", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, DialogController::applyControlVisibility);
                }
            });
            XposedBridge.hookAllMethods(dialogClass, "toggleRingerDrawer", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    guardedWhenNeeded(param.thisObject, controller -> {
                        controller.applyControlVisibility();
                        controller.refresh();
                    });
                }
            });
            // VolumeDialogImpl is normally process-long-lived, but SystemUI can replace it after
            // configuration changes. Release our callback/listeners when that lifecycle ends.
            XposedBridge.hookAllMethods(dialogClass, "destroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    disposeController(param.thisObject);
                }
            });
            systemUiHookInstalled = true;
            XposedBridge.log(TAG + ": Nothing VolumeDialogImpl hook ready");
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": hook unavailable, stock panel kept: " + error);
        }
    }

    /**
     * Delays only screen-off volume presses while music is active. Releasing before the timeout
     * performs one ordinary volume step; holding past it sends next/previous and suppresses the
     * repeated volume changes normally produced by a held hardware key.
     */
    private static void hookScreenOffMediaKeys(ClassLoader classLoader) {
        try {
            Class<?> policyClass = XposedHelpers.findClass(PHONE_WINDOW_MANAGER, classLoader);
            XposedBridge.hookAllMethods(policyClass, "interceptKeyBeforeQueueing",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                KeyEvent event = null;
                                for (Object argument : param.args) {
                                    if (argument instanceof KeyEvent) {
                                        event = (KeyEvent) argument;
                                        break;
                                    }
                                }
                                if (event == null) return;

                                ScreenOffMediaKeyController controller =
                                        MEDIA_KEY_CONTROLLERS.get(param.thisObject);
                                if (controller == null) {
                                    Context context = (Context) XposedHelpers.getObjectField(
                                            param.thisObject, "mContext");
                                    if (!keyFeaturesEnabled(context)) return;
                                    Object hostHandler = XposedHelpers.getObjectField(
                                            param.thisObject, "mHandler");
                                    controller = new ScreenOffMediaKeyController(
                                            context,
                                            hostHandler instanceof Handler
                                                    ? (Handler) hostHandler
                                                    : new Handler(Looper.getMainLooper()));
                                    MEDIA_KEY_CONTROLLERS.put(param.thisObject, controller);
                                }
                                if (controller.handle(event)) {
                                    param.setResult(0);
                                }
                            } catch (Throwable error) {
                                XposedBridge.log(TAG + ": media-key fail-open: " + error);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": screen-off media-key hook ready");
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": media-key hook unavailable; stock keys kept: " + error);
        }
    }

    private static final class ScreenOffMediaKeyController {
        private final Context context;
        private final Handler handler;
        private final AudioManager audio;
        private final PowerManager power;
        private final Map<Integer, PressState> presses = new LinkedHashMap<>();
        private final Map<Integer, Long> interactiveLastStep = new LinkedHashMap<>();

        ScreenOffMediaKeyController(Context context, Handler handler) {
            this.context = context;
            this.handler = handler;
            this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            this.power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        }

        synchronized boolean handle(KeyEvent event) {
            int keyCode = event.getKeyCode();
            if (keyCode != KeyEvent.KEYCODE_VOLUME_UP
                    && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false;

            if (power.isInteractive()) return handleInteractive(event);

            PressState existing = presses.get(keyCode);
            if (existing == null && !screenOffShortcutEligible()) {
                return handleScreenOffStepOnly(event);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (existing == null) {
                    PressState state = new PressState();
                    state.longPress = () -> triggerLongPress(keyCode, state);
                    presses.put(keyCode, state);
                    handler.postDelayed(state.longPress, LONG_PRESS_MS);
                } else if (!existing.triggered && (event.isLongPress()
                        || (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0)) {
                    triggerLongPress(keyCode, existing);
                }
                return true;
            }

            if (event.getAction() == KeyEvent.ACTION_UP && existing != null) {
                handler.removeCallbacks(existing.longPress);
                presses.remove(keyCode);
                if (!existing.triggered) {
                    int direction = keyCode == KeyEvent.KEYCODE_VOLUME_UP
                            ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                    adjustVolume(direction, true, false);
                }
                return true;
            }

            return existing != null;
        }

        private boolean handleInteractive(KeyEvent event) {
            if (audio.getMode() != AudioManager.MODE_NORMAL) return false;
            int percent = settingInt(VOLUME_STEP_PERCENT, 0);
            boolean alwaysMedia = settingInt(ALWAYS_MEDIA_VOLUME, 0) != 0;
            if (percent == 0 && !alwaysMedia) return false;

            int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_UP) {
                interactiveLastStep.remove(keyCode);
                return true;
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;

            long now = android.os.SystemClock.uptimeMillis();
            Long last = interactiveLastStep.get(keyCode);
            if (event.getRepeatCount() == 0
                    || last == null || now - last >= KEY_REPEAT_RATE_LIMIT_MS) {
                int direction = keyCode == KeyEvent.KEYCODE_VOLUME_UP
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                adjustVolume(direction, alwaysMedia, true);
                interactiveLastStep.put(keyCode, now);
            }
            return true;
        }

        private boolean handleScreenOffStepOnly(KeyEvent event) {
            if (audio.getMode() != AudioManager.MODE_NORMAL || !audio.isMusicActive()) return false;
            int percent = settingInt(VOLUME_STEP_PERCENT, 0);
            boolean alwaysMedia = settingInt(ALWAYS_MEDIA_VOLUME, 0) != 0;
            if (percent == 0 && !alwaysMedia) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                int direction = event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                adjustVolume(direction, true, false);
            }
            return true;
        }

        private void adjustVolume(int direction, boolean mediaOnly, boolean showUi) {
            int percent = settingInt(VOLUME_STEP_PERCENT, 0);
            int steps = 1;
            if (percent > 0) {
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                // Keep all four percentage choices distinct on this device's 16-level stream.
                steps = Math.max(1, (max * percent + 99) / 100);
            }
            for (int i = 0; i < steps; i++) {
                int flags = showUi && i == 0 ? AudioManager.FLAG_SHOW_UI : 0;
                if (mediaOnly) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags);
                } else {
                    audio.adjustSuggestedStreamVolume(
                            direction, AudioManager.USE_DEFAULT_STREAM_TYPE, flags);
                }
            }
        }

        private int settingInt(String key, int fallback) {
            try {
                return Settings.Secure.getInt(context.getContentResolver(), key, fallback);
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private boolean screenOffShortcutEligible() {
            try {
                return Settings.Secure.getInt(
                        context.getContentResolver(), MEDIA_KEYS_SWITCH, 0) != 0
                        && !power.isInteractive()
                        && audio.getMode() == AudioManager.MODE_NORMAL
                        && audio.isMusicActive();
            } catch (Throwable error) {
                XposedBridge.log(TAG + ": media-key eligibility failed: " + error);
                return false;
            }
        }

        private synchronized void triggerLongPress(int keyCode, PressState state) {
            if (state.triggered || presses.get(keyCode) != state) return;
            state.triggered = true;
            int mediaKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            long now = android.os.SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, mediaKey, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(
                    now, now + 10L, KeyEvent.ACTION_UP, mediaKey, 0));
            XposedBridge.log(TAG + ": screen-off long " + keyName(keyCode)
                    + " -> " + (mediaKey == KeyEvent.KEYCODE_MEDIA_NEXT ? "next" : "previous"));
        }

        private static String keyName(int keyCode) {
            return keyCode == KeyEvent.KEYCODE_VOLUME_UP ? "volume-up" : "volume-down";
        }

        private static final class PressState {
            Runnable longPress;
            boolean triggered;
        }
    }

    private interface ControllerAction {
        void run(DialogController controller) throws Throwable;
    }

    private static boolean keyFeaturesEnabled(Context context) {
        try {
            return Settings.Secure.getInt(
                    context.getContentResolver(), MEDIA_KEYS_SWITCH, 0) != 0
                    || Settings.Secure.getInt(
                    context.getContentResolver(), VOLUME_STEP_PERCENT, 0) != 0
                    || Settings.Secure.getInt(
                    context.getContentResolver(), ALWAYS_MEDIA_VOLUME, 0) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void guardedWhenNeeded(Object dialog, ControllerAction action) {
        synchronized (CONTROLLERS) {
            if (!CONTROLLERS.containsKey(dialog)) {
                try {
                    Context context = (Context) XposedHelpers.getObjectField(dialog, "mContext");
                    if (Settings.Secure.getInt(
                            context.getContentResolver(), KILL_SWITCH, 0) == 0) return;
                } catch (Throwable ignored) {
                    return;
                }
            }
        }
        guarded(dialog, action);
    }

    private static void guarded(Object dialog, ControllerAction action) {
        try {
            DialogController controller;
            synchronized (CONTROLLERS) {
                controller = CONTROLLERS.get(dialog);
                if (controller == null) {
                    controller = new DialogController(dialog);
                    CONTROLLERS.put(dialog, controller);
                }
            }
            action.run(controller);
        } catch (Throwable error) {
            XposedBridge.log(TAG + ": guarded error, stock behavior continues: " + error);
        }
    }

    private static void disposeController(Object dialog) {
        DialogController controller;
        synchronized (CONTROLLERS) {
            controller = CONTROLLERS.remove(dialog);
        }
        if (controller != null) controller.dispose();
    }

    private static final class DialogController {
        // A WeakHashMap is not weak if its value strongly references its key. Keep the host weak so
        // old VolumeDialogImpl instances can actually be collected after a configuration change.
        private final WeakReference<Object> dialogRef;
        private final Context context;
        private final Handler main;
        private final AudioManager audio;
        private final ActivityManager activity;
        private final PackageManager packages;
        private final boolean nativeIsVolumeKeyOnRight;
        private final Map<String, Float> sessionLevels = new LinkedHashMap<>();
        private final Map<String, Set<Integer>> sessionPlayerIds = new LinkedHashMap<>();
        private final Map<String, AppRow> appRows = new LinkedHashMap<>();
        private final Map<String, PlayerGroup> currentPlayers = new LinkedHashMap<>();
        private ViewGroup systemRows;
        private HorizontalScrollView scroller;
        private LinearLayout rowStrip;
        private LinearLayout appRowsContainer;
        private View.OnLayoutChangeListener rowsLayoutListener;
        private AudioManager.AudioPlaybackCallback playbackCallback;
        private int contentExtent;
        private boolean refreshQueued;
        private boolean disposed;
        private final Runnable anchorRunnable = () -> {
            HorizontalScrollView current = scroller;
            if (current == null || rowStrip == null || disposed) return;
            int max = Math.max(0, contentExtent - current.getWidth());
            current.scrollTo(resolvePanelRight() ? max : 0, 0);
        };
        private final Runnable viewportRunnable = () -> {
            HorizontalScrollView current = scroller;
            if (current == null || disposed || !(current.getParent() instanceof ViewGroup)) return;
            constrainViewport((ViewGroup) current.getParent());
            anchorToPanelSide();
        };
        private boolean showing;
        private boolean changingDrawer;
        private int appliedSide = -1;
        private int appliedCaptions = -1;
        private int appliedSettings = -1;
        private boolean appliedModuleEnabled = true;

        DialogController(Object dialog) {
            this.dialogRef = new WeakReference<>(dialog);
            this.context = (Context) XposedHelpers.getObjectField(dialog, "mContext");
            Object hostHandler = XposedHelpers.getObjectField(dialog, "mHandler");
            this.main = hostHandler instanceof Handler
                    ? (Handler) hostHandler : new Handler(Looper.getMainLooper());
            this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            this.activity = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            this.packages = context.getPackageManager();
            this.nativeIsVolumeKeyOnRight = XposedHelpers.getBooleanField(
                    dialog, "mIsVolumeKeyOnRight");
        }

        private Object host() {
            Object host = dialogRef.get();
            if (host == null) throw new IllegalStateException("VolumeDialogImpl was released");
            return host;
        }

        void prepareDialogInflation() {
            XposedHelpers.setBooleanField(host(), "mIsVolumeKeyOnRight",
                    enabled() ? resolvePanelRight() : nativeIsVolumeKeyOnRight);
        }

        void prepareForShow() {
            boolean moduleEnabled = enabled();
            int side = settingInt(PANEL_SIDE, SIDE_AUTO);
            int captions = settingInt(SHOW_CAPTIONS, 1);
            int settings = settingInt(SHOW_SETTINGS, 1);
            if (appliedSide >= 0 && (moduleEnabled != appliedModuleEnabled
                    || side != appliedSide
                    || captions != appliedCaptions || settings != appliedSettings)) {
                // VolumeDialogImpl replaces mDialog when mConfigChanged is set, but does not
                // dismiss the previous Window first when that flag is injected immediately
                // before showH(). Dismiss synchronously to avoid leaking the old left/right
                // panel alongside the newly inflated one.
                try {
                    Object oldDialog = XposedHelpers.getObjectField(host(), "mDialog");
                    if (oldDialog instanceof Dialog && ((Dialog) oldDialog).isShowing()) {
                        ((Dialog) oldDialog).dismiss();
                    }
                } catch (Throwable error) {
                    XposedBridge.log(TAG + ": old dialog cleanup unavailable: " + error);
                }
                XposedHelpers.setBooleanField(host(), "mConfigChanged", true);
            }
            prepareDialogInflation();
            // showH() schedules dismissal using this field, so update it before the original
            // method runs. Applying it only from refresh() would affect the following opening.
            applyPanelTimeout();
        }

        void onDialogInflated() {
            appliedModuleEnabled = enabled();
            appliedSide = settingInt(PANEL_SIDE, SIDE_AUTO);
            appliedCaptions = settingInt(SHOW_CAPTIONS, 1);
            appliedSettings = settingInt(SHOW_SETTINGS, 1);
            if (!appliedModuleEnabled) return;
            wrapSystemRows();
            registerPlaybackCallback();
            applyPanelTimeout();
            applyControlVisibility();
            refresh();
            XposedBridge.log(TAG + ": inflated side=" + appliedSide
                    + " resolvedRight=" + resolvePanelRight());
        }

        void onShown() {
            main.post(this::applyAutoExpand);
        }

        private boolean enabled() {
            try {
                return Settings.Secure.getInt(
                        context.getContentResolver(), KILL_SWITCH, 0) != 0;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private int settingInt(String key, int fallback) {
            try {
                return Settings.Secure.getInt(context.getContentResolver(), key, fallback);
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private boolean resolvePanelRight() {
            int side = settingInt(PANEL_SIDE, SIDE_AUTO);
            if (side == SIDE_LEFT) return false;
            if (side == SIDE_RIGHT) return true;
            return nativeIsVolumeKeyOnRight;
        }

        private void applyPanelTimeout() {
            int timeout = settingInt(PANEL_TIMEOUT_MS, 3_000);
            if (timeout < 1_000 || timeout > 10_000 || timeout % 1_000 != 0) {
                timeout = 3_000;
            }
            XposedHelpers.setIntField(host(), "mDialogTimeoutMillis", timeout);
        }

        void applyControlVisibility() {
            if (!enabled()) return;
            if (settingInt(SHOW_CAPTIONS, 1) == 0) {
                hideFieldView("mODICaptionsView");
                hideFieldView("mODICaptionsTooltipView");
                hideFieldView("mODICaptionsTooltipViewStub");
            }
            if (settingInt(SHOW_SETTINGS, 1) == 0) {
                // Nothing reuses mSettingsView: it is the drawer chevron while collapsed and
                // becomes the Settings shortcut after expansion. Never remove the only native
                // way to open the drawer.
                boolean expanded = false;
                try {
                    expanded = XposedHelpers.getBooleanField(host(), "mIsRingerDrawerOpen");
                } catch (Throwable ignored) {
                }
                setFieldVisibility("mSettingsView", expanded ? View.GONE : View.VISIBLE);
            }
        }

        private void hideFieldView(String field) {
            setFieldVisibility(field, View.GONE);
        }

        private void setFieldVisibility(String field, int visibility) {
            try {
                Object value = XposedHelpers.getObjectField(host(), field);
                if (value instanceof View) ((View) value).setVisibility(visibility);
            } catch (Throwable ignored) {
            }
        }

        private void applyAutoExpand() {
            if (!showing || !enabled() || changingDrawer) return;
            boolean shouldExpand = settingInt(AUTO_EXPAND, 0) != 0 && !currentPlayers.isEmpty();
            boolean expanded;
            try {
                expanded = XposedHelpers.getBooleanField(host(), "mIsRingerDrawerOpen");
            } catch (Throwable ignored) {
                return;
            }
            if (expanded == shouldExpand) return;
            changingDrawer = true;
            try {
                XposedHelpers.callMethod(host(), "toggleRingerDrawer", shouldExpand);
            } catch (Throwable error) {
                XposedBridge.log(TAG + ": auto-expand unavailable: " + error);
            } finally {
                main.postDelayed(() -> changingDrawer = false, 350L);
            }
        }

        /** Wrap the existing row strip without replacing or re-inflating any stock SystemUI row. */
        private void wrapSystemRows() {
            ViewGroup current = (ViewGroup) XposedHelpers.getObjectField(host(), "mDialogRowsView");
            if (current == null) return;
            if (current == systemRows && scroller != null) return;

            detachRowsListener();
            scroller = null;
            rowStrip = null;
            appRowsContainer = null;
            systemRows = current;
            appRows.clear();
            if (!(systemRows.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) systemRows.getParent();
            if (parent instanceof HorizontalScrollView) {
                scroller = (HorizontalScrollView) parent;
                return;
            }

            int index = parent.indexOfChild(systemRows);
            ViewGroup.LayoutParams original = systemRows.getLayoutParams();
            parent.removeView(systemRows);

            scroller = new ClippedHorizontalScrollView(context);
            scroller.setTag("dotsuite_systemui_scroller");
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroller.setFillViewport(false);
            // Nothing's stock row strip was allowed to draw beyond its wrapper. That made a fifth
            // slider hang outside the panel instead of reading as scrollable content. Keep a hard
            // four-column viewport; additional stock/app rows stay reachable by swiping sideways.
            scroller.setClipChildren(true);
            scroller.setClipToPadding(true);
            scroller.setHorizontalFadingEdgeEnabled(true);
            scroller.setFadingEdgeLength(dp(14));
            scroller.setContentDescription("Uygulama sesleri");

            ViewGroup.LayoutParams wrapper;
            if (original instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams source = (LinearLayout.LayoutParams) original;
                LinearLayout.LayoutParams target = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, source.height);
                target.weight = source.weight;
                wrapper = target;
            } else {
                wrapper = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, original.height);
            }
            parent.addView(scroller, index, wrapper);
            rowStrip = new LinearLayout(context);
            rowStrip.setOrientation(LinearLayout.HORIZONTAL);
            rowStrip.setClipChildren(false);
            rowStrip.setClipToPadding(false);
            scroller.addView(rowStrip, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, original.height));
            rowStrip.addView(systemRows, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, original.height));
            appRowsContainer = new LinearLayout(context);
            appRowsContainer.setOrientation(LinearLayout.HORIZONTAL);
            appRowsContainer.setClipChildren(false);
            appRowsContainer.setClipToPadding(false);
            rowStrip.addView(appRowsContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, original.height));
            rowsLayoutListener = (view, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> constrainViewport(parent);
            rowStrip.addOnLayoutChangeListener(rowsLayoutListener);
            scroller.post(() -> constrainViewport(parent));
        }

        private void detachRowsListener() {
            if (rowStrip != null && rowsLayoutListener != null) {
                rowStrip.removeOnLayoutChangeListener(rowsLayoutListener);
            }
            rowsLayoutListener = null;
        }

        /** Size the wrapper to whole rows so no clipped half-slider can leak outside the panel. */
        private void constrainViewport(ViewGroup parent) {
            if (scroller == null || systemRows == null || rowStrip == null
                    || systemRows.getChildCount() == 0) return;
            View lastVisible = null;
            int visibleCount = 0;
            int contentRight = systemRows.getPaddingLeft();
            int sliderId = systemResource("volume_row_slider", "id");
            for (int i = 0; i < systemRows.getChildCount(); i++) {
                View child = systemRows.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || child.getMeasuredWidth() <= 0) continue;
                // Nothing keeps a visible drawer placeholder in this container. It has the same
                // width as a stream row but no visible volume slider, so counting its right edge
                // creates a completely blank final scroll position. Only real slider rows belong
                // to the continuous strip.
                View slider = sliderId == 0 ? null : child.findViewById(sliderId);
                if (!(slider instanceof SeekBar) || slider.getVisibility() != View.VISIBLE) continue;
                int right = child.getRight();
                ViewGroup.LayoutParams childParams = child.getLayoutParams();
                if (childParams instanceof ViewGroup.MarginLayoutParams) {
                    right += ((ViewGroup.MarginLayoutParams) childParams).rightMargin;
                }
                contentRight = Math.max(contentRight, right);
                visibleCount++;
                if (visibleCount <= MAX_VISIBLE_COLUMNS) lastVisible = child;
            }
            if (lastVisible == null || lastVisible.getRight() <= 0
                    || parent.getMeasuredWidth() <= 0) return;

            // Never resize or add children to Nothing's native row container. Its animator owns
            // those exact child indices. Nothing also leaves a full-width visible placeholder at
            // the end of this container. Pull the sibling app strip back over only that unused
            // tail: native child indices stay intact while the user sees one continuous sequence
            // of real sliders instead of a blank page between Alarm and the first app.
            int unusedNativeTail = Math.max(0, systemRows.getWidth() - contentRight);
            ViewGroup.LayoutParams appParamsRaw = appRowsContainer == null
                    ? null : appRowsContainer.getLayoutParams();
            if (appParamsRaw instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams appParams = (LinearLayout.LayoutParams) appParamsRaw;
                int wantedMargin = -unusedNativeTail;
                if (appParams.leftMargin != wantedMargin) {
                    appParams.leftMargin = wantedMargin;
                    appRowsContainer.setLayoutParams(appParams);
                }
            }
            int appWidth = appRowsContainer == null ? 0 : appRowsContainer.getWidth();
            contentExtent = contentRight + appWidth;
            int available = parent.getMeasuredWidth() - parent.getPaddingLeft()
                    - parent.getPaddingRight();
            // Rows overlap slightly in Nothing's layout. Multiplying measuredWidth therefore
            // overestimates four columns; the laid-out right edge is the exact viewport boundary.
            int wanted = Math.min(available, lastVisible.getRight());
            ViewGroup.LayoutParams params = scroller.getLayoutParams();
            if (wanted > 0 && params.width != wanted) {
                params.width = wanted;
                scroller.setLayoutParams(params);
            }
        }

        private void registerPlaybackCallback() {
            if (playbackCallback != null) return;
            playbackCallback = new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                    reconcileSessions();
                    if (showing) refresh();
                }
            };
            try {
                audio.registerAudioPlaybackCallback(playbackCallback, main);
            } catch (Throwable error) {
                playbackCallback = null;
                XposedBridge.log(TAG + ": playback callback unavailable: " + error);
            }
        }

        private void unregisterPlaybackCallback() {
            if (playbackCallback == null) return;
            try {
                audio.unregisterAudioPlaybackCallback(playbackCallback);
            } catch (Throwable ignored) {
            }
            playbackCallback = null;
        }

        void refresh() {
            if (disposed || refreshQueued) return;
            refreshQueued = true;
            main.post(() -> {
                refreshQueued = false;
                if (disposed) return;
                try {
                    if (!enabled()) {
                        resetSessionLevels();
                        hideAppRows();
                        unregisterPlaybackCallback();
                        return;
                    }
                    registerPlaybackCallback();
                    wrapSystemRows();
                    if (systemRows == null) return;
                    boolean expanded = XposedHelpers.getBooleanField(
                            host(), "mIsRingerDrawerOpen");
                    Map<String, PlayerGroup> active = discoverPlayers();
                    currentPlayers.clear();
                    currentPlayers.putAll(active);
                    syncRows(active, expanded);
                    applyPanelTimeout();
                    applyControlVisibility();
                    scheduleViewportUpdate();
                } catch (Throwable error) {
                    XposedBridge.log(TAG + ": refresh skipped: " + error);
                    hideAppRows();
                }
            });
        }

        private void anchorToPanelSide() {
            if (scroller == null || rowStrip == null) return;
            scroller.removeCallbacks(anchorRunnable);
            scroller.post(anchorRunnable);
        }

        private void scheduleViewportUpdate() {
            if (scroller == null || rowStrip == null) return;
            // Visibility changes lay out the child after refresh(). Keep exactly one short task;
            // unlike the old 420 ms workaround this cannot accumulate across callbacks.
            rowStrip.requestLayout();
            scroller.removeCallbacks(viewportRunnable);
            scroller.postDelayed(viewportRunnable, 32L);
        }

        private Map<String, PlayerGroup> discoverPlayers() {
            Map<String, PlayerGroup> result = new LinkedHashMap<>();
            List<AudioPlaybackConfiguration> configs;
            try {
                configs = audio.getActivePlaybackConfigurations();
            } catch (Throwable error) {
                return result;
            }
            for (AudioPlaybackConfiguration config : configs) {
                try {
                    if (!isPlayerActive(config)) continue;
                    AudioAttributes attributes = config.getAudioAttributes();
                    int usage = attributes == null
                            ? AudioAttributes.USAGE_UNKNOWN : attributes.getUsage();
                    if (usage != AudioAttributes.USAGE_UNKNOWN &&
                            usage != AudioAttributes.USAGE_MEDIA &&
                            usage != AudioAttributes.USAGE_GAME &&
                            usage != AudioAttributes.USAGE_ASSISTANT) continue;

                    int uid = hiddenInt(config, "getClientUid", -1);
                    int pid = hiddenInt(config, "getClientPid", -1);
                    if (uid < 0 || uid == android.os.Process.SYSTEM_UID) continue;
                    String packageName = resolvePackage(uid, pid);
                    if (packageName == null || context.getPackageName().equals(packageName)) continue;

                    PlayerGroup group = result.get(packageName);
                    if (group == null) {
                        group = new PlayerGroup(packageName, uid, appLabel(packageName));
                        result.put(packageName, group);
                    }
                    group.configs.add(config);
                } catch (Throwable ignored) {
                    // A single malformed/hidden playback configuration cannot affect SystemUI.
                }
            }
            return result;
        }

        private String resolvePackage(int uid, int pid) {
            try {
                List<ActivityManager.RunningAppProcessInfo> running =
                        activity.getRunningAppProcesses();
                if (running != null) {
                    for (ActivityManager.RunningAppProcessInfo process : running) {
                        if (process.pid != pid || process.processName == null) continue;
                        String candidate = process.processName.split(":", 2)[0];
                        try {
                            packages.getApplicationInfo(candidate, 0);
                            return candidate;
                        } catch (PackageManager.NameNotFoundException ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            String[] names = packages.getPackagesForUid(uid);
            return names == null || names.length == 0 ? null : names[0];
        }

        private String appLabel(String packageName) {
            try {
                ApplicationInfo info = packages.getApplicationInfo(packageName, 0);
                return String.valueOf(packages.getApplicationLabel(info));
            } catch (Throwable ignored) {
                int dot = packageName.lastIndexOf('.');
                return dot < 0 ? packageName : packageName.substring(dot + 1);
            }
        }

        private void syncRows(Map<String, PlayerGroup> active, boolean expanded) {
            Map<String, PlayerGroup> displayed = new LinkedHashMap<>();
            for (PlayerGroup group : active.values()) {
                if (displayed.size() >= 8) break;
                displayed.put(group.packageName, group);
            }
            Set<String> stale = new LinkedHashSet<>(appRows.keySet());
            stale.removeAll(displayed.keySet());
            for (String packageName : stale) {
                AppRow row = appRows.remove(packageName);
                if (row != null && row.root.getParent() == appRowsContainer) {
                    appRowsContainer.removeView(row.root);
                }
            }

            for (PlayerGroup group : displayed.values()) {
                AppRow row = appRows.get(group.packageName);
                if (row == null) {
                    row = inflateSystemRow(group);
                    if (row == null) continue;
                    XposedBridge.log(TAG + ": adding native row for " + group.packageName);
                    appRows.put(group.packageName, row);
                    // Keep every stock row at its original child index. Nothing's drawer animator
                    // caches those indices; inserting between media/ringer/notification/alarm
                    // leaves the last native row's shell visible while its contents animate at an
                    // old coordinate, which looks exactly like a blank slider-width page.
                    appRowsContainer.addView(row.root);
                    // Never write a player multiplier merely because a row appeared. A fresh
                    // player or an Android Auto route must always begin at Android's own 1.0.
                }
                row.group = group;
                row.root.setVisibility(expanded ? View.VISIBLE : View.GONE);
                if (!row.seeking) {
                    float value = sessionLevels.getOrDefault(group.packageName, 1f);
                    row.slider.setProgress(Math.round(value * 1000f));
                }
            }
        }

        /** Inflate the exact Nothing SystemUI row; no app-specific visual is introduced. */
        private AppRow inflateSystemRow(PlayerGroup group) {
            int layout = systemResource("volume_dialog_row", "layout");
            int sliderId = systemResource("volume_row_slider", "id");
            int iconId = systemResource("volume_row_icon", "id");
            int headerId = systemResource("volume_row_header", "id");
            if (layout == 0 || sliderId == 0 || iconId == 0) return null;

            View root = LayoutInflater.from(context).inflate(layout, systemRows, false);
            root.setId(View.generateViewId());
            root.setTag("dotsuite_app:" + group.packageName);
            SeekBar slider = root.findViewById(sliderId);
            ImageButton icon = root.findViewById(iconId);
            TextView header = headerId == 0 ? null : root.findViewById(headerId);
            if (header != null) header.setVisibility(View.GONE);

            slider.setMin(0);
            slider.setMax(1000);
            slider.setProgress(Math.round(
                    sessionLevels.getOrDefault(group.packageName, 1f) * 1000f));
            slider.setContentDescription(group.label + " uygulama sesi");
            try {
                Drawable appIcon = packages.getApplicationIcon(group.packageName);
                icon.setImageDrawable(appIcon);
                icon.setImageTintList(null);
                icon.setPadding(dp(8), dp(8), dp(8), dp(8));
                icon.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            } catch (Throwable ignored) {
                icon.setImageResource(android.R.drawable.ic_media_play);
                icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            }
            icon.setContentDescription(group.label);

            AppRow row = new AppRow(root, slider, icon, group);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    float level = Math.max(0f, Math.min(1f, progress / 1000f));
                    sessionLevels.put(row.group.packageName, level);
                    sessionPlayerIds.put(row.group.packageName, playerIds(row.group));
                    applyLevel(row.group, level);
                    keepOpen();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    row.seeking = true;
                    keepOpen();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    row.seeking = false;
                    keepOpen();
                }
            });
            return row;
        }

        private void applyLevel(PlayerGroup group, float level) {
            float safe = Math.max(0f, Math.min(1f, level));
            for (Object config : new ArrayList<>(group.configs)) {
                try {
                    Object proxy = XposedHelpers.callMethod(config, "getPlayerProxy");
                    if (proxy != null) XposedHelpers.callMethod(proxy, "setVolume", safe);
                } catch (Throwable error) {
                    XposedBridge.log(TAG + ": player volume skipped: " + error);
                }
            }
        }

        /**
         * A player multiplier belongs only to the exact player ids the user touched. Never carry
         * it across a recreated MediaPlayer, route change or Android Auto hand-off: doing that can
         * make the framework show a high stream level while the new output remains attenuated.
         */
        private void reconcileSessions() {
            if (!enabled()) {
                resetSessionLevels();
                return;
            }
            if (sessionLevels.isEmpty()) return;
            Map<String, PlayerGroup> active = discoverPlayers();
            for (String packageName : new LinkedHashSet<>(sessionLevels.keySet())) {
                PlayerGroup group = active.get(packageName);
                Set<Integer> originalIds = sessionPlayerIds.get(packageName);
                Set<Integer> currentIds = group == null ? Collections.emptySet() : playerIds(group);
                if (group == null || originalIds == null || !originalIds.equals(currentIds)) {
                    // If a route change kept any player alive, explicitly release our multiplier;
                    // if all old players ended there is nothing left to restore.
                    if (group != null) applyLevel(group, 1f);
                    sessionLevels.remove(packageName);
                    sessionPlayerIds.remove(packageName);
                    XposedBridge.log(TAG + ": released changed volume session for " + packageName);
                }
            }
        }

        private Set<Integer> playerIds(PlayerGroup group) {
            Set<Integer> ids = new LinkedHashSet<>();
            for (Object config : group.configs) {
                int id = hiddenInt(config, "getPlayerInterfaceId", Integer.MIN_VALUE);
                ids.add(id == Integer.MIN_VALUE ? System.identityHashCode(config) : id);
            }
            return ids;
        }

        /** Restore only multipliers this controller changed, then forget them. */
        private void resetSessionLevels() {
            if (sessionLevels.isEmpty()) return;
            Map<String, PlayerGroup> active = discoverPlayers();
            for (PlayerGroup group : active.values()) {
                if (sessionLevels.containsKey(group.packageName)) applyLevel(group, 1f);
            }
            XposedBridge.log(TAG + ": restored " + sessionLevels.keySet() + " player volumes");
            sessionLevels.clear();
            sessionPlayerIds.clear();
        }

        private int hiddenInt(Object target, String method, int fallback) {
            try {
                Object result = XposedHelpers.callMethod(target, method);
                return result instanceof Integer ? (Integer) result : fallback;
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private boolean isPlayerActive(Object config) {
            int state = hiddenInt(config, "getPlayerState", -1);
            if (state >= 0) return state == 2;
            try {
                Object active = XposedHelpers.callMethod(config, "isActive");
                if (active instanceof Boolean) return (Boolean) active;
            } catch (Throwable ignored) {
            }
            return false;
        }

        private int systemResource(String name, String type) {
            return context.getResources().getIdentifier(name, type, SYSTEM_UI);
        }

        private int dp(float value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        private void keepOpen() {
            try {
                XposedHelpers.callMethod(host(), "rescheduleTimeoutH");
            } catch (Throwable ignored) {
            }
        }

        private void hideAppRows() {
            for (AppRow row : appRows.values()) row.root.setVisibility(View.GONE);
        }

        void dispose() {
            if (disposed) return;
            // PlayerProxy volume is process-local; restore only values changed by this controller
            // before dropping its bookkeeping.
            try {
                resetSessionLevels();
            } catch (Throwable ignored) {
            }
            disposed = true;
            showing = false;
            if (scroller != null) {
                scroller.removeCallbacks(anchorRunnable);
                scroller.removeCallbacks(viewportRunnable);
            }
            detachRowsListener();
            unregisterPlaybackCallback();
            for (AppRow row : appRows.values()) {
                row.slider.setOnSeekBarChangeListener(null);
            }
            appRows.clear();
            currentPlayers.clear();
            sessionLevels.clear();
            sessionPlayerIds.clear();
            systemRows = null;
            scroller = null;
            rowStrip = null;
            appRowsContainer = null;
        }
    }

    private static final class PlayerGroup {
        final String packageName;
        final int uid;
        final String label;
        final List<Object> configs = new ArrayList<>();

        PlayerGroup(String packageName, int uid, String label) {
            this.packageName = packageName;
            this.uid = uid;
            this.label = label;
        }
    }

    private static final class AppRow {
        final View root;
        final SeekBar slider;
        final ImageButton icon;
        PlayerGroup group;
        boolean seeking;

        AppRow(View root, SeekBar slider, ImageButton icon, PlayerGroup group) {
            this.root = root;
            this.slider = slider;
            this.icon = icon;
            this.group = group;
        }
    }

    /** SystemUI temporarily disables descendant clipping during row animations; enforce it here. */
    private static final class ClippedHorizontalScrollView extends HorizontalScrollView {
        private final int touchSlop;
        private float downX;
        private float downY;

        ClippedHorizontalScrollView(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            // Rotated native SeekBars request exclusive touch on DOWN. Keep interception enabled;
            // onInterceptTouchEvent still yields every vertical gesture back to the slider.
            super.requestDisallowInterceptTouchEvent(false);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                return super.onInterceptTouchEvent(event);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (dx > touchSlop && dx > dy) return true;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int checkpoint = canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            super.dispatchDraw(canvas);
            canvas.restoreToCount(checkpoint);
        }
    }
}
