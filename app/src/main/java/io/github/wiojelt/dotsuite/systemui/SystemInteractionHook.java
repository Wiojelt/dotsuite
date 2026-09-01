package io.github.wiojelt.dotsuite.systemui;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import java.lang.reflect.Member;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.GesturePolicy;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/** Nothing Android 16 only, called after the entry point's device/API guard. No overlay or polling. */
final class SystemInteractionHook {
    private static final String CONTROLLER = "dotsuite.notch.controller";
    private static final String APP = "io.github.wiojelt.dotsuite";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        try {
            Class<?> type = XposedHelpers.findClass("com.android.systemui.statusbar.phone.PhoneStatusBarView", loader);
            XposedHelpers.findAndHookMethod(type, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    View view = (View) p.thisObject;
                    try {
                        if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(view, CONTROLLER + ".failed"))) return;
                        Controller controller = (Controller) XposedHelpers.getAdditionalInstanceField(view, CONTROLLER);
                        if (controller == null && view.isAttachedToWindow()) {
                            controller = new Controller(view, loader);
                            XposedHelpers.setAdditionalInstanceField(view, CONTROLLER, controller);
                        }
                        if (controller != null && controller.handle((MotionEvent) p.args[0], p.method)) p.setResult(true);
                    } catch (Throwable error) {
                        Controller controller = (Controller) XposedHelpers.removeAdditionalInstanceField(view, CONTROLLER);
                        if (controller != null) controller.dispose();
                        XposedHelpers.setAdditionalInstanceField(view, CONTROLLER + ".failed", true);
                        report("notch disabled for this view; native touches kept", error);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(type, "onDetachedFromWindow", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Controller controller = (Controller) XposedHelpers.removeAdditionalInstanceField(p.thisObject, CONTROLLER);
                    if (controller != null) controller.dispose();
                }
            });
            XposedBridge.log("DotSuite: native notch hooks installed (disabled by default)");
        } catch (Throwable error) { report("notch unavailable; native touches kept", error); }

        try {
            Class<?> carrier = XposedHelpers.findClass("com.android.keyguard.CarrierTextManager", loader);
            // Change ONLY the ready SIM's name before native error/airplane/satellite formatting.
            XposedHelpers.findAndHookMethod(carrier, "getCarrierTextForSimState", int.class, CharSequence.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if ((int) p.args[0] != 5 || p.args[1] == null) return; // SIM_STATE_READY
                                Context context = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                                String label = Settings.Secure.getString(context.getContentResolver(), PersonalizationPolicy.CARRIER_LABEL);
                                if (label != null && !label.isEmpty()
                                        && PersonalizationPolicy.acceptsString(PersonalizationPolicy.CARRIER_LABEL, label)) p.args[1] = label;
                            } catch (Throwable error) { report("carrier label unavailable; original kept", error); }
                        }
                    });
        } catch (Throwable error) { report("carrier hook unavailable", error); }
    }

    private static synchronized void report(String stage, Throwable error) {
        if (REPORTED.add(stage)) XposedBridge.log("DotSuite: " + stage + " (" + error.getClass().getSimpleName() + ")");
    }

    private static final class Controller {
        final View view;
        final Context context;
        final ClassLoader loader;
        final Handler handler = new Handler(Looper.getMainLooper());
        final int slop;
        final float swipe;
        final ContentObserver observer;
        boolean enabled, doubleSleep, haptics, tracking, held, moved, secondTap;
        boolean pendingTap, pendingNotch;
        int tap, doubleTap, hold, left, right;
        float downX, downY, pendingX, pendingY;
        long pendingAt, lastAction;
        MotionEvent down;
        final Runnable single = () -> {
            if (pendingTap && pendingNotch) perform(tap);
            pendingTap = false;
        };
        final Runnable longPress = () -> {
            if (tracking && !moved && hold != 0) {
                held = true;
                pendingTap = false;
                handler.removeCallbacks(single);
                perform(hold);
            }
        };

        Controller(View view, ClassLoader loader) {
            this.view = view;
            this.context = view.getContext();
            this.loader = loader;
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            swipe = 36f * context.getResources().getDisplayMetrics().density;
            observer = new ContentObserver(handler) {
                @Override public void onChange(boolean selfChange) { refresh(); }
            };
            String[] keys = {PersonalizationPolicy.NOTCH_ENABLED, PersonalizationPolicy.NOTCH_TAP,
                    PersonalizationPolicy.NOTCH_DOUBLE, PersonalizationPolicy.NOTCH_HOLD,
                    PersonalizationPolicy.NOTCH_LEFT, PersonalizationPolicy.NOTCH_RIGHT,
                    PersonalizationPolicy.NOTCH_HAPTICS, PersonalizationPolicy.STATUS_DOUBLE_SLEEP};
            try {
                for (String key : keys) context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
                refresh();
            } catch (Throwable error) {
                // A partially registered controller must not leak observers on every touch event.
                dispose();
                throw error;
            }
        }

        int read(String key, int fallback) { return Settings.Secure.getInt(context.getContentResolver(), key, fallback); }
        void refresh() {
            try {
                enabled = read(PersonalizationPolicy.NOTCH_ENABLED, 0) == 1;
                doubleSleep = read(PersonalizationPolicy.STATUS_DOUBLE_SLEEP, 0) == 1;
                haptics = read(PersonalizationPolicy.NOTCH_HAPTICS, 1) == 1;
                tap = read(PersonalizationPolicy.NOTCH_TAP, 0);
                doubleTap = read(PersonalizationPolicy.NOTCH_DOUBLE, 0);
                hold = read(PersonalizationPolicy.NOTCH_HOLD, 0);
                left = read(PersonalizationPolicy.NOTCH_LEFT, 0);
                right = read(PersonalizationPolicy.NOTCH_RIGHT, 0);
                if (!enabled) cancel();
            } catch (Throwable error) { enabled = false; doubleSleep = false; report("notch preferences unavailable", error); }
        }

        boolean unlocked() {
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            AccessibilityManager access = context.getSystemService(AccessibilityManager.class);
            return keyguard != null && !keyguard.isKeyguardLocked()
                    && (access == null || !access.isTouchExplorationEnabled())
                    && context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        }

        boolean inNotch(MotionEvent event) {
            if (android.os.Build.VERSION.SDK_INT < 29) return false;
            WindowInsets insets = view.getRootWindowInsets();
            if (insets == null || insets.getDisplayCutout() == null) return false;
            Rect bounds = new Rect(insets.getDisplayCutout().getBoundingRectTop());
            if (bounds.isEmpty()) return false; // no fabricated invisible target on another display
            int margin = Math.round(12 * context.getResources().getDisplayMetrics().density);
            bounds.inset(-margin, -margin);
            return bounds.contains((int) event.getRawX(), (int) event.getRawY());
        }

        boolean handle(MotionEvent event, Member original) throws Throwable {
            int action = event.getActionMasked();
            if ((!enabled && !doubleSleep) || !unlocked()) { cancel(); return false; }
            if (action == MotionEvent.ACTION_DOWN) {
                tracking = enabled && inNotch(event) && (tap != 0 || doubleTap != 0 || hold != 0 || left != 0 || right != 0);
                moved = false; held = false;
                downX = event.getRawX(); downY = event.getRawY();
                secondTap = pendingTap && tracking == pendingNotch
                        && event.getEventTime() - pendingAt <= ViewConfiguration.getDoubleTapTimeout()
                        && GesturePolicy.tap(downX - pendingX, downY - pendingY, slop * 2f);
                if (secondTap) { handler.removeCallbacks(single); pendingTap = false; }
                if (down != null) down.recycle();
                down = MotionEvent.obtain(event);
                if (tracking && hold != 0) handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return tracking;
            }
            if (down == null) return false;
            float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
            int motion = GesturePolicy.motion(dx, dy, event.getPointerCount(), slop, swipe);
            if (action == MotionEvent.ACTION_CANCEL) { boolean consumed = tracking; cancel(); return consumed; }
            if (motion == GesturePolicy.SHADE || motion == GesturePolicy.CANCEL) {
                boolean wasTracking = tracking;
                handler.removeCallbacks(longPress); handler.removeCallbacks(single); pendingTap = false;
                if (wasTracking) XposedBridge.invokeOriginalMethod(original, view, new Object[]{down});
                tracking = false; moved = true; down.recycle(); down = null;
                // The current MOVE/POINTER event goes to the native method after the replayed DOWN.
                return false;
            }
            if (!GesturePolicy.tap(dx, dy, slop)) { moved = true; handler.removeCallbacks(longPress); }
            if (action != MotionEvent.ACTION_UP) return tracking;
            handler.removeCallbacks(longPress);
            boolean consumed = tracking;
            if (tracking && !held) {
                if (motion == GesturePolicy.LEFT) perform(left);
                else if (motion == GesturePolicy.RIGHT) perform(right);
                else if (!moved) completeTap(event, true);
            } else if (!tracking && doubleSleep && !moved) completeTap(event, false);
            tracking = false; down.recycle(); down = null;
            return consumed;
        }

        void completeTap(MotionEvent event, boolean notch) {
            if (secondTap) { perform(notch ? doubleTap : PersonalizationPolicy.SLEEP); secondTap = false; }
            else if (notch && doubleTap == 0) perform(tap);
            else {
                pendingTap = true; pendingNotch = notch; pendingAt = event.getEventTime();
                pendingX = event.getRawX(); pendingY = event.getRawY();
                handler.removeCallbacks(single);
                handler.postDelayed(single, ViewConfiguration.getDoubleTapTimeout());
            }
        }

        void cancel() {
            handler.removeCallbacks(single); handler.removeCallbacks(longPress);
            tracking = false; pendingTap = false; secondTap = false;
            if (down != null) { down.recycle(); down = null; }
        }
        void dispose() {
            cancel();
            try { context.getContentResolver().unregisterContentObserver(observer); }
            catch (Throwable ignored) {}
        }

        Object dependency(String name) {
            return XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.android.systemui.Dependency", loader),
                    "get", XposedHelpers.findClass(name, loader));
        }

        @SuppressLint("WrongConstant") // "statusbar" is the hidden, verified SystemUI service key.
        void perform(int action) {
            if (action == 0 || !PersonalizationPolicy.isAction(action) || !unlocked()) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastAction < 400) return;
            lastAction = now;
            try {
                AudioManager audio = context.getSystemService(AudioManager.class);
                switch (action) {
                    case PersonalizationPolicy.SCREENSHOT: {
                        Object input = context.getSystemService(Context.INPUT_SERVICE);
                        // SystemUI owns INJECT_EVENTS; the system screenshot pipeline keeps secure windows protected.
                        for (int phase : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                            KeyEvent key = new KeyEvent(now, now, phase, KeyEvent.KEYCODE_SYSRQ, 0);
                            Object ok = XposedHelpers.callMethod(input, "injectInputEvent", key, 0);
                            if (Boolean.FALSE.equals(ok)) throw new IllegalStateException("screenshot rejected");
                        }
                        break;
                    }
                    case PersonalizationPolicy.FLASHLIGHT: {
                        Object flashlight = dependency("com.android.systemui.statusbar.policy.FlashlightController");
                        if (!Boolean.TRUE.equals(XposedHelpers.callMethod(flashlight, "isAvailable")))
                            throw new IllegalStateException("flashlight unavailable");
                        XposedHelpers.callMethod(flashlight, "setFlashlight",
                                !((Boolean) XposedHelpers.callMethod(flashlight, "isEnabled")));
                        break;
                    }
                    case PersonalizationPolicy.PLAY_PAUSE:
                    case PersonalizationPolicy.NEXT:
                    case PersonalizationPolicy.PREVIOUS: {
                        if (audio == null || audio.getMode() != AudioManager.MODE_NORMAL) return;
                        int code = action == 3 ? KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                                : action == 4 ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
                        break;
                    }
                    case PersonalizationPolicy.VOLUME_PANEL:
                        if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                        break;
                    case PersonalizationPolicy.NOTIFICATIONS:
                        XposedHelpers.callMethod(context.getSystemService("statusbar"), "expandNotificationsPanel"); break;
                    case PersonalizationPolicy.QUICK_SETTINGS:
                        XposedHelpers.callMethod(context.getSystemService("statusbar"), "expandSettingsPanel", (Object) null); break;
                    case PersonalizationPolicy.SLEEP:
                        XposedHelpers.callMethod(context.getSystemService(PowerManager.class), "goToSleep", now); break;
                    case PersonalizationPolicy.CAMERA:
                        context.startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); break;
                    case PersonalizationPolicy.QUICK_DOCK:
                        context.startActivity(new Intent().setClassName(APP, APP + ".dock.QuickDockActivity")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
                        break;
                    default:
                        if (PersonalizationPolicy.isCaptureAction(action)) {
                            context.startActivity(new Intent().setClassName(APP, APP + ".capture.CaptureShortcutActivity")
                                    .putExtra("capture_action", action)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
                        }
                }
                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                XposedBridge.log("DotSuite: notch action=" + action + " dispatched");
            } catch (Throwable error) {
                report("notch action " + action + " unavailable", error);
                Toast.makeText(context, "DotSuite: shortcut unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
