package io.github.wiojelt.dotsuite.systemui;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.HashMap;
import java.util.Map;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/** Uses only the native long-press callback; never consumes raw power/SOS/chord events. */
final class PowerTorchHook {
    private static boolean installed;
    private static CameraManager camera;
    private static final Map<String, Boolean> torch = new HashMap<>();
    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        try {
            Class<?> rule = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager$PowerKeyRule", loader);
            XposedHelpers.findAndHookMethod(rule, "onLongPress", long.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object policy = XposedHelpers.getObjectField(param.thisObject, "this$0");
                        Context context = (Context) XposedHelpers.getObjectField(policy, "mContext");
                        if (Settings.Secure.getInt(context.getContentResolver(), PersonalizationPolicy.POWER_TORCH, 0) != 1) return;
                        Object detector = XposedHelpers.getObjectField(policy, "mSingleKeyGestureDetector");
                        if (!Boolean.TRUE.equals(XposedHelpers.callMethod(detector, "beganFromNonInteractive"))) return;
                        // The OEM already owns screen-off long-press: never replace it.
                        if (XposedHelpers.getBooleanField(policy, "mSupportLongPressPowerWhenNonInteractive")) {
                            status(context, 2); return;
                        }
                        AudioManager audio = context.getSystemService(AudioManager.class);
                        if (audio == null || audio.getMode() != AudioManager.MODE_NORMAL) return;
                        if (camera == null) {
                            camera = context.getSystemService(CameraManager.class);
                            camera.registerTorchCallback(new CameraManager.TorchCallback() {
                                @Override public void onTorchModeChanged(String id, boolean enabled) { synchronized (torch) { torch.put(id, enabled); } }
                                @Override public void onTorchModeUnavailable(String id) { synchronized (torch) { torch.remove(id); } }
                            }, new Handler(Looper.getMainLooper()));
                            status(context, 3); return; // Wait for authoritative state, never guess.
                        }
                        for (String id : camera.getCameraIdList()) {
                            CameraCharacteristics info = camera.getCameraCharacteristics(id);
                            if (!Boolean.TRUE.equals(info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
                                    || !Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING))) continue;
                            Boolean enabled;
                            synchronized (torch) { enabled = torch.get(id); }
                            if (enabled == null) continue;
                            camera.setTorchMode(id, !enabled);
                            param.setResult(null);
                            status(context, 1);
                            return;
                        }
                        status(context, 4);
                    } catch (Throwable error) {
                        XposedBridge.log("DotSuite: power torch fail-open: " + error.getClass().getSimpleName());
                    }
                }
            });
            installed = true;
        } catch (Throwable error) {
            XposedBridge.log("DotSuite: native power rule unavailable; untouched");
        }
    }
    private static void status(Context context, int result) {
        try { Settings.Secure.putInt(context.getContentResolver(), PersonalizationPolicy.POWER_TORCH_STATUS, result); }
        catch (Throwable ignored) {}
    }
}
