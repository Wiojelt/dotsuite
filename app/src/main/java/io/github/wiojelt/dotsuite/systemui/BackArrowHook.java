package io.github.wiojelt.dotsuite.systemui;

import android.content.Context;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.BackArrowPolicy;
import io.github.wiojelt.dotsuite.drawing.BackArrowRenderer;

/** Opt-in, Nothing-only path decorator. Native detection, commit, cancel, color and alpha stay owned by SystemUI. */
final class BackArrowHook {
    private static final String STATE = "dotsuite.backArrow";
    private static boolean installed, reported;
    private static synchronized void report(Throwable error) {
        if (reported) return;
        reported = true;
        XposedBridge.log("DotSuite: back arrow disabled; stock kept (" + error.getClass().getSimpleName() + ")");
    }
    static synchronized void install(ClassLoader loader) {
        if (installed || Build.VERSION.SDK_INT != 36 || !"Asteroids".equals(Build.DEVICE)) return;
        installed = true;
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            Class<?> type = XposedHelpers.findClass("com.nothing.systemui.navigationbar.gestural.BackPanelEx", loader);
            Method draw = type.getDeclaredMethod("calculateArrowPath", Path.class, Paint.class, float.class, float.class);
            if (draw.getReturnType() != Path.class) throw new NoSuchMethodException();
            hooks.add(XposedBridge.hookMethod(type.getDeclaredConstructor(Context.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    State state = null;
                    try {
                        if (XposedHelpers.getAdditionalInstanceField(p.thisObject, STATE) != null) return;
                        state = new State((Context) p.args[0]);
                        XposedHelpers.setAdditionalInstanceField(p.thisObject, STATE, state);
                        state.start();
                    } catch (Throwable error) { if (state != null) state.stop(); report(error); }
                }
            }));
            hooks.add(XposedBridge.hookMethod(draw, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    // Original always runs first; a failed original is never masked.
                    if (p.hasThrowable()) return;
                    State state = null;
                    try {
                        state = (State) XposedHelpers.getAdditionalInstanceField(p.thisObject, STATE);
                        if (state == null) return;
                        Options o = state.options;
                        if (!o.enabled || o.style == 0 || !(p.getResult() instanceof Path)) return;
                        Paint paint = (Paint) p.args[1];
                        if (paint.getStyle() != Paint.Style.FILL) return; // contract drift: no mutation
                        Path scratch = state.scratch.get();
                        if (BackArrowRenderer.draw(scratch, o.style, o.motion, o.size,
                                (float) p.args[2], (float) p.args[3], paint.getStrokeWidth(), state.reference)) {
                            ((Path) p.getResult()).set(scratch);
                        }
                    } catch (Throwable error) { if (state != null) state.stop(); report(error); }
                }
            }));
            XposedBridge.log("DotSuite: back arrow " + io.github.wiojelt.dotsuite.BuildConfig.VERSION_NAME + " prepared; default is stock");
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) try { hook.unhook(); } catch (Throwable ignored) {}
            report(error);
        }
    }
    private static final class Options {
        final boolean enabled;
        final int style, motion, size;
        Options(boolean enabled, int style, int motion, int size) {
            this.enabled = enabled; this.style = style; this.motion = motion; this.size = size;
        }
    }
    private static final Options STOCK = new Options(false, 0, 0, 100);
    private static final class State {
        final ContentResolver resolver;
        final float reference;
        // SystemUI owns a single BackPanelEx. No View, Activity or instance-key retained here.
        final ThreadLocal<Path> scratch = ThreadLocal.withInitial(Path::new);
        volatile Options options = STOCK;
        boolean registered, failed;
        final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { refresh(); }
        };
        State(Context context) {
            resolver = context.getApplicationContext().getContentResolver();
            int id = context.getResources().getIdentifier("navigation_edge_active_arrow_length", "dimen", "com.android.systemui");
            reference = id == 0 ? 0 : context.getResources().getDimension(id);
            if (!Float.isFinite(reference) || reference <= 0) throw new IllegalStateException();
        }
        synchronized void start() {
            if (failed || registered) return;
            // Mark before registering so a partial failure unregisters everything.
            registered = true;
            for (String key : BackArrowPolicy.KEYS)
                resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer);
            refresh();
        }
        synchronized void refresh() {
            if (failed) return;
            try {
                boolean enabled = Settings.Secure.getInt(resolver, BackArrowPolicy.ENABLED, 0) == 1;
                int style = Settings.Secure.getInt(resolver, BackArrowPolicy.STYLE, 0);
                int motion = Settings.Secure.getInt(resolver, BackArrowPolicy.MOTION, 0);
                int size = Settings.Secure.getInt(resolver, BackArrowPolicy.SIZE, 100);
                if (!BackArrowPolicy.accepts(BackArrowPolicy.STYLE, Integer.toString(style))
                        || !BackArrowPolicy.accepts(BackArrowPolicy.MOTION, Integer.toString(motion))
                        || !BackArrowPolicy.accepts(BackArrowPolicy.SIZE, Integer.toString(size))) {
                    options = STOCK; return;
                }
                if (Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1) == 0) motion = 0;
                options = new Options(enabled, style, motion, size);
            } catch (Throwable error) { stop(); report(error); }
        }
        synchronized void stop() {
            options = STOCK; failed = true;
            if (registered) {
                try { resolver.unregisterContentObserver(observer); } catch (Throwable ignored) {}
                registered = false;
            }
        }
    }
}
