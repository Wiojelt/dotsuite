package io.github.wiojelt.dotsuite.systemui;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/** Resolver-only adapter hook. No global low-RAM spoof, ranking edits or file deletion. */
final class ShareSheetHook {
    private static boolean installed;
    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        for (String name : new String[]{"com.android.intentresolver.ChooserListAdapter", "com.android.internal.app.ChooserListAdapter"}) {
            try {
                Class<?> adapter = XposedHelpers.findClass(name, loader);
                XposedHelpers.findAndHookMethod(adapter, "getServiceTargetCount", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Context context = AndroidAppHelper.currentApplication();
                            if (context != null && Settings.Secure.getInt(context.getContentResolver(),
                                    PersonalizationPolicy.HIDE_DIRECT_SHARE, 0) == 1) param.setResult(0);
                        } catch (Throwable ignored) { /* stock sharing remains available */ }
                    }
                });
                installed = true;
                XposedBridge.log("DotSuite: resolver contact-row hook installed");
                return;
            } catch (Throwable ignored) { /* OEM adapter not supported */ }
        }
        XposedBridge.log("DotSuite: resolver adapter not found; stock sharing kept");
    }
}
