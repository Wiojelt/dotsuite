package io.github.wiojelt.dotsuite.systemui;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.view.View;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/** Called only after the entry point's Asteroids / API 36 guard. No timer, overlay or layout edits. */
final class NativeVisualHook {
    private static final String STATE = "dotsuite.nativevisual.state";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        try {
            Class<?> clock = XposedHelpers.findClass("com.android.systemui.statusbar.policy.Clock", loader);
            installState(clock, PersonalizationPolicy.CLOCK_DAY, true);
            XposedHelpers.findAndHookMethod(clock, "getSmallTime", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        FlagState state = (FlagState) XposedHelpers.getAdditionalInstanceField(p.thisObject, STATE);
                        if (state == null || !state.enabled || !(p.getResult() instanceof CharSequence)) return;
                        CharSequence original = (CharSequence) p.getResult();
                        if (original.length() == 0) return;
                        View view = (View) p.thisObject;
                        Locale locale = view.getResources().getConfiguration().getLocales().get(0);
                        Calendar calendar = (Calendar) XposedHelpers.getObjectField(view, "mCalendar");
                        if (calendar == null) return;
                        // Preserve spans in the original clock, including the native AM/PM sizing.
                        p.setResult(new SpannableStringBuilder(state.weekday(calendar, locale)).append(" ").append(original));
                    } catch (Throwable error) { report("weekday unavailable; native time kept", error); }
                }
            });
        } catch (Throwable error) { report("clock class unavailable", error); }
        boolean found = false;
        for (String name : new String[] {
                "com.android.systemui.navigationbar.gestural.NavigationHandle",
                "com.android.systemui.navigationbar.views.NavigationHandle"}) {
            Class<?> handle = XposedHelpers.findClassIfExists(name, loader);
            if (handle == null) continue;
            try {
                installState(handle, PersonalizationPolicy.HIDE_NAV_PILL, false);
                XposedHelpers.findAndHookMethod(handle, "onDraw", Canvas.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            FlagState state = (FlagState) XposedHelpers.getAdditionalInstanceField(p.thisObject, STATE);
                            if (state != null && state.enabled) p.setResult(null);
                        } catch (Throwable error) { report("gesture indicator kept", error); }
                    }
                });
                found = true;
            } catch (Throwable error) { report("gesture handle incompatible", error); }
        }
        if (!found) report("no supported gesture handle; stock drawing kept", new ClassNotFoundException());
    }

    private static void installState(Class<?> type, String key, boolean clock) {
        XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (!(p.thisObject instanceof View) || XposedHelpers.getAdditionalInstanceField(p.thisObject, STATE) != null) return;
                    View view = (View) p.thisObject;
                    FlagState state = new FlagState(view, key, clock);
                    XposedHelpers.setAdditionalInstanceField(view, STATE, state);
                    view.addOnAttachStateChangeListener(state);
                    if (view.isAttachedToWindow()) state.onViewAttachedToWindow(view);
                } catch (Throwable error) { report("preference observer unavailable; native view kept", error); }
            }
        });
    }

    private static synchronized void report(String message, Throwable error) {
        if (REPORTED.add(message)) XposedBridge.log("DotSuite: " + message + " (" + error.getClass().getSimpleName() + ")");
    }

    private static final class FlagState implements View.OnAttachStateChangeListener {
        // Xposed stores extra fields in a weak-key map; never retain its key from its value.
        final WeakReference<View> target;
        final ContentResolver resolver;
        final String key;
        final boolean clock;
        final ContentObserver observer;
        boolean registered, enabled;
        int cachedDate = -1;
        Locale cachedLocale;
        String cachedZone, cachedDay;

        String weekday(Calendar calendar, Locale locale) {
            int date = calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR);
            String zone = calendar.getTimeZone().getID();
            if (date != cachedDate || !locale.equals(cachedLocale) || !zone.equals(cachedZone)) {
                SimpleDateFormat day = new SimpleDateFormat("EEE", locale);
                day.setTimeZone(calendar.getTimeZone());
                cachedDay = day.format(calendar.getTime());
                cachedDate = date; cachedLocale = locale; cachedZone = zone;
            }
            return cachedDay;
        }

        FlagState(View view, String key, boolean clock) {
            target = new WeakReference<>(view);
            resolver = view.getContext().getApplicationContext().getContentResolver();
            this.key = key;
            this.clock = clock;
            observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { refresh(); }
            };
        }
        @Override public void onViewAttachedToWindow(View view) {
            try {
                if (!registered) {
                    resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
                    registered = true;
                }
                refresh();
            } catch (Throwable error) {
                enabled = false;
                onViewDetachedFromWindow(view);
                report("native appearance observer failed", error);
            }
        }
        @Override public void onViewDetachedFromWindow(View view) {
            enabled = false;
            if (registered) {
                try { resolver.unregisterContentObserver(observer); }
                catch (Throwable error) { report("observer cleanup failed", error); }
                registered = false;
            }
        }
        void refresh() {
            View view = target.get();
            if (view == null) { onViewDetachedFromWindow(null); return; }
            try {
                enabled = Settings.Secure.getInt(resolver, key, 0) == 1;
                if (clock) XposedHelpers.callMethod(view, "updateClock");
                else view.invalidate();
            } catch (Throwable error) {
                enabled = false;
                report("native appearance refresh failed; stock kept", error);
            }
        }
    }
}
