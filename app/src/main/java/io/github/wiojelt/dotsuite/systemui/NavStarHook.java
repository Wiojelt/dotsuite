package io.github.wiojelt.dotsuite.systemui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * NavStar Hook:
 * 1. Hide Navigation Bar Gesture Pill (home_handle height 0, gone) for full screen edge-to-edge.
 * 2. Transparent Navigation Bar handle.
 * 3. Long-press back gesture to kill foreground application.
 */
@SuppressLint("NewApi")
public final class NavStarHook {
    private static final String TAG = "DotSuite.NavStar";
    private static boolean installedSystemUi = false;
    private static boolean installedLauncher = false;
    private static ContentResolver contentResolver;
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    private static volatile boolean hideNavPill = false;
    private static volatile boolean backGestureKill = false;
    private static volatile int backGestureTimeoutMs = 450;

    public static synchronized void installSystemUi(ClassLoader loader) {
        if (installedSystemUi) return;
        installedSystemUi = true;
        XposedBridge.log(TAG + ": installSystemUi()");

        hookNavigationBarView(loader);
        hookBackGestureKill(loader);
    }

    public static synchronized void installLauncher(ClassLoader loader) {
        if (installedLauncher) return;
        installedLauncher = true;
        XposedBridge.log(TAG + ": installLauncher()");

        hookStashedHandleView(loader);
    }

    private static void hookNavigationBarView(ClassLoader loader) {
        String[] navBarClasses = {
                "com.android.systemui.navigationbar.views.NavigationBarView",
                "com.android.systemui.navigationbar.NavigationBarView",
                "com.android.systemui.statusbar.phone.NavigationBarView"
        };

        for (String className : navBarClasses) {
            Class<?> navBarClass = XposedHelpers.findClassIfExists(className, loader);
            if (navBarClass != null) {
                XC_MethodHook hook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        applyHidePill(param.thisObject);
                    }
                };

                try {
                    XposedBridge.hookAllMethods(navBarClass, "onFinishInflate", hook);
                    XposedBridge.hookAllMethods(navBarClass, "updateStates", hook);
                    XposedBridge.hookAllMethods(navBarClass, "onLayout", hook);
                    XposedBridge.log(TAG + ": Hooked " + className);
                    break;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Failed hooking " + className + ": " + t);
                }
            }
        }
    }

    private static void hookStashedHandleView(ClassLoader loader) {
        Class<?> handleClass = XposedHelpers.findClassIfExists("com.android.launcher3.taskbar.StashedHandleView", loader);
        if (handleClass != null) {
            XposedBridge.hookAllConstructors(handleClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object obj = param.thisObject;
                        if (obj instanceof View) {
                            View view = (View) obj;
                            initObserver(view.getContext());
                            if (hideNavPill) {
                                view.setBackground(null);
                                try {
                                    XposedHelpers.setIntField(obj, "mStashedHandleDarkColor", 0);
                                    XposedHelpers.setIntField(obj, "mStashedHandleLightColor", 0);
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": StashedHandleView error: " + t);
                    }
                }
            });
        }
    }

    private static void hookBackGestureKill(ClassLoader loader) {
        Class<?> backClass = XposedHelpers.findClassIfExists(
                "com.nothing.systemui.navigationbar.gestural.BackPanelEx", loader);
        if (backClass == null) {
            backClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler", loader);
        }

        if (backClass != null) {
            XposedBridge.hookAllMethods(backClass, "onTouchEvent", new XC_MethodHook() {
                private long touchDownTime = 0;
                private boolean actionTriggered = false;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!backGestureKill) return;
                        MotionEvent event = (MotionEvent) param.args[0];
                        if (event == null) return;

                        int action = event.getActionMasked();
                        if (action == MotionEvent.ACTION_DOWN) {
                            touchDownTime = SystemClock.uptimeMillis();
                            actionTriggered = false;
                        } else if (action == MotionEvent.ACTION_MOVE) {
                            if (!actionTriggered && touchDownTime > 0
                                    && (SystemClock.uptimeMillis() - touchDownTime > backGestureTimeoutMs)) {
                                actionTriggered = true;
                                killForegroundApp((View) param.thisObject);
                            }
                        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            touchDownTime = 0;
                            actionTriggered = false;
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + ": BackGestureKill hooked");
        }
    }

    private static void killForegroundApp(View view) {
        if (view == null) return;
        try {
            Context context = view.getContext();
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                            String pkg = process.pkgList != null && process.pkgList.length > 0 ? process.pkgList[0] : null;
                            if (pkg != null && !"com.android.systemui".equals(pkg)
                                    && !"com.nothing.launcher".equals(pkg)
                                    && !"android".equals(pkg)
                                    && !"io.github.wiojelt.dotsuite".equals(pkg)) {
                                am.killBackgroundProcesses(pkg);
                                XposedBridge.log(TAG + ": Killed foreground app: " + pkg);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": killForegroundApp failed: " + t);
        }
    }

    private static void applyHidePill(Object navBar) {
        if (!(navBar instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) navBar;
        Context context = vg.getContext();
        initObserver(context);

        if (!hideNavPill) return;

        try {
            int homeHandleId = context.getResources().getIdentifier("home_handle", "id", "com.android.systemui");
            if (homeHandleId == 0) {
                homeHandleId = context.getResources().getIdentifier("home_handle", "id", "com.android.systemui.res");
            }
            if (homeHandleId != 0) {
                View handle = vg.findViewById(homeHandleId);
                if (handle != null) {
                    handle.setVisibility(View.GONE);
                    handle.setAlpha(0.0f);
                    ViewGroup.LayoutParams lp = handle.getLayoutParams();
                    if (lp != null && lp.height != 0) {
                        lp.height = 0;
                        handle.setLayoutParams(lp);
                    }
                    ViewParent parent = handle.getParent();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).requestLayout();
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": applyHidePill error: " + t);
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
                    Settings.Secure.getUriFor(PersonalizationPolicy.HIDE_NAV_PILL), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.BACK_GESTURE_KILL), false, observer);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.BACK_GESTURE_TIMEOUT), false, observer);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": initObserver error: " + t);
        }
    }

    private static void updateSettings() {
        if (contentResolver == null) return;
        try {
            hideNavPill = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.HIDE_NAV_PILL, 0) == 1;
            backGestureKill = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.BACK_GESTURE_KILL, 0) == 1;
            backGestureTimeoutMs = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.BACK_GESTURE_TIMEOUT, 450);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": updateSettings error: " + t);
        }
    }
}
