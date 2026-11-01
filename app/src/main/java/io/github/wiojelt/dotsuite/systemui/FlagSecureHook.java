package io.github.wiojelt.dotsuite.systemui;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.SurfaceControl;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * FlagSecure Hook:
 * Bypasses FLAG_SECURE protection so screenshots and screen recordings can be captured
 * across all applications (banking apps, secure chats, streaming, etc.).
 */
@SuppressLint("NewApi")
public final class FlagSecureHook {
    private static final String TAG = "DotSuite.FlagSecure";
    private static boolean installed = false;
    private static ContentResolver contentResolver;
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static volatile boolean bypassEnabled = false;

    public static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        XposedBridge.log(TAG + ": install()");

        try {
            Class<?> windowStateClass = XposedHelpers.findClassIfExists("com.android.server.wm.WindowState", loader);
            if (windowStateClass != null) {
                XposedBridge.hookAllMethods(windowStateClass, "isSecureLocked", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        ensureObserver(param.thisObject);
                        if (bypassEnabled) {
                            param.setResult(false);
                        }
                    }
                });
                XposedBridge.log(TAG + ": WindowState.isSecureLocked hooked");
            }

            Class<?> surfaceControlBuilderClass = XposedHelpers.findClassIfExists("android.view.SurfaceControl$Builder", loader);
            if (surfaceControlBuilderClass != null) {
                XposedBridge.hookAllMethods(surfaceControlBuilderClass, "setSecure", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (bypassEnabled && param.args.length > 0) {
                            param.args[0] = false;
                        }
                    }
                });
                XposedBridge.log(TAG + ": SurfaceControl.Builder.setSecure hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": install error: " + t);
        }
    }

    private static void ensureObserver(Object windowState) {
        if (contentResolver != null) return;
        try {
            Context context = (Context) XposedHelpers.callMethod(windowState, "getContext");
            if (context != null) {
                contentResolver = context.getContentResolver();
                updateSettings();

                ContentObserver observer = new ContentObserver(uiHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        updateSettings();
                    }
                };

                contentResolver.registerContentObserver(
                        Settings.Secure.getUriFor(PersonalizationPolicy.BYPASS_FLAG_SECURE), false, observer);
            }
        } catch (Throwable ignored) {}
    }

    private static void updateSettings() {
        if (contentResolver == null) return;
        try {
            bypassEnabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.BYPASS_FLAG_SECURE, 0) == 1;
        } catch (Throwable ignored) {}
    }
}
