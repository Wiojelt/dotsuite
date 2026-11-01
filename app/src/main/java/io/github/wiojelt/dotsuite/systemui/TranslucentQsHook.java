package io.github.wiojelt.dotsuite.systemui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Set;

import androidx.compose.ui.graphics.ColorKt;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy;

/**
 * Translucent Quick Settings & Frosted Glass Tiles hook for Nothing OS 3.0 / Asteroids.
 * Hooks:
 * 1. ShadeColors#shadePanelScrimBehind -> removes pitch black overlay, allowing wallpaper blur to shine through.
 * 2. ShadeColors#shadePanel -> replaces opaque shade panel with frosted glass tint.
 * 3. QuickSettingsShadeOverlayContentViewModel -> enables transparency for the Dual Shade overlay.
 * 4. TileContentColorsKt -> replaces Compose QS tile background with translucent frosted glass.
 * 5. ScrimController & DepthController -> maintains wallpaper blur and light scrim.
 */
final class TranslucentQsHook {
    private static final String TAG = "DotSuite.TranslucentQs";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;
    private static volatile boolean enabled = true;
    private static volatile int qsTransparency = 50; // 0..100
    private static volatile int qsTileStyle = 1; // 0: Stock, 1: Frosted Glass, 2: Liquid Glass
    private static ContentResolver contentResolver;
    private static ContentObserver contentObserver;

    // Translucent frosted glass colors matching Nothing OS 3.0 widget & folder styling
    private static final int COLOR_FROSTED_INACTIVE = 0x2EFFFFFF;     // ~18% translucent white glass
    private static final int COLOR_FROSTED_UNAVAILABLE = 0x1AFFFFFF;  // ~10% translucent white glass
    private static final int COLOR_FROSTED_ACTIVE = 0xEEFFFFFF;       // ~93% crisp frosted white
    private static final int COLOR_FROSTED_SCRIM = 0x24000000;        // ~14% subtle dark scrim tint
    private static final int COLOR_FROSTED_PANEL = 0x1FFFFFFF;        // ~12% subtle frosted glass panel tint

    // Resource IDs
    private static int idQsTileInactiveBg = -1;
    private static int idQsTileActiveBg = -1;
    private static int idQsTileDisableBg = -1;
    private static int idQsTileUnavailBg = -1;
    private static int idQsTileMedUnavailBg = -1;
    private static int idQsTileLargeUnavailBg = -1;
    private static int idQsTileOffBg = -1;
    private static int idNtScrimBehind1 = -1;
    private static int idNtScrimBehind2 = -1;
    private static int idNtScrimBehindColor = -1;
    private static int idNotifScrimBg = -1;

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        XposedBridge.log(TAG + ": install() called");

        hookApplication(loader);
        hookShadeColors(loader);
        hookQuickSettingsOverlay(loader);
        hookTileContentColors(loader);
        hookScrimController(loader);
        hookDepthController(loader);
        hookResourceColors(loader);
    }

    private static void hookApplication(ClassLoader loader) {
        try {
            Class<?> appClass = XposedHelpers.findClassIfExists("com.android.systemui.SystemUIApplication", loader);
            if (appClass != null) {
                XposedBridge.hookAllMethods(appClass, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            initObserver((Context) param.thisObject);
                        } catch (Throwable t) {
                            report("SystemUIApplication initObserver", t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            report("hookApplication", t);
        }
    }

    static void initObserver(Context context) {
        if (contentResolver != null || context == null) return;
        try {
            contentResolver = context.getApplicationContext().getContentResolver();
            enabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.TRANSLUCENT_QS, 1) == 1;
            qsTransparency = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_TRANSPARENCY, 50);
            qsTileStyle = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_TILE_STYLE, 1);
            contentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    enabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.TRANSLUCENT_QS, 1) == 1;
                    qsTransparency = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_TRANSPARENCY, 50);
                    qsTileStyle = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_TILE_STYLE, 1);
                    XposedBridge.log(TAG + ": updated: enabled=" + enabled + ", transparency=" + qsTransparency + ", style=" + qsTileStyle);
                }
            };
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.TRANSLUCENT_QS),
                    false, contentObserver);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.QS_TRANSPARENCY),
                    false, contentObserver);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.QS_TILE_STYLE),
                    false, contentObserver);
            XposedBridge.log(TAG + ": observer registered, initialState=" + enabled);
        } catch (Throwable t) {
            report("failed to init observer", t);
        }
    }

    private static void hookShadeColors(ClassLoader loader) {
        try {
            Class<?> shadeColorsClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.shade.ui.ShadeColors", loader);
            if (shadeColorsClass != null) {
                XposedBridge.hookAllMethods(shadeColorsClass, "shadePanelScrimBehind", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (enabled) {
                            param.setResult(COLOR_FROSTED_SCRIM);
                        }
                    }
                });

                XposedBridge.hookAllMethods(shadeColorsClass, "shadePanel", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (enabled) {
                            param.setResult(COLOR_FROSTED_PANEL);
                        }
                    }
                });

                XposedBridge.hookAllMethods(shadeColorsClass, "notificationScrim", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (enabled) {
                            param.setResult(COLOR_FROSTED_SCRIM);
                        }
                    }
                });
                XposedBridge.log(TAG + ": ShadeColors hooked");
            }
        } catch (Throwable t) {
            report("shade colors hook", t);
        }
    }

    private static void hookQuickSettingsOverlay(ClassLoader loader) {
        try {
            Class<?> overlayVmClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayContentViewModel", loader);
            if (overlayVmClass != null) {
                XposedBridge.hookAllMethods(overlayVmClass, "isTransparencyEnabled", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (enabled) {
                            param.setResult(true);
                        }
                    }
                });
                XposedBridge.log(TAG + ": QuickSettingsShadeOverlayContentViewModel transparency hooked");
            }
        } catch (Throwable t) {
            report("quick settings overlay vm hook", t);
        }
    }

    private static void hookTileContentColors(ClassLoader loader) {
        try {
            Class<?> tileColorsKt = XposedHelpers.findClassIfExists(
                    "com.nothing.systemui.qs.workspace.workspace.TileContentColorsKt", loader);
            if (tileColorsKt != null) {
                XposedBridge.hookAllMethods(tileColorsKt, "resolveTileContentColors", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!enabled || param.getResult() == null || param.args.length == 0) return;
                        try {
                            int state = ((Integer) param.args[0]).intValue();
                            Object colors = param.getResult();

                            int activeColorInt;
                            int inactiveColorInt;
                            int unavailColorInt;

                            int alphaInactive = Math.max(15, Math.min(180, (int) (qsTransparency * 1.5f)));

                            switch (qsTileStyle) {
                                case 2: // Liquid Glass
                                    inactiveColorInt = (alphaInactive << 24) | 0x00CAE8;
                                    activeColorInt = 0xDE00D8FF;
                                    unavailColorInt = (Math.max(10, alphaInactive / 2) << 24) | 0x00CAE8;
                                    break;
                                case 3: // Nothing Red Dot Accent
                                    inactiveColorInt = (alphaInactive << 24) | 0x00FFFFFF;
                                    activeColorInt = 0xFFD71921;
                                    unavailColorInt = (Math.max(10, alphaInactive / 2) << 24) | 0x00FFFFFF;
                                    break;
                                default: // Frosted Glass
                                    inactiveColorInt = (alphaInactive << 24) | 0x00FFFFFF;
                                    activeColorInt = 0xEEFFFFFF;
                                    unavailColorInt = (Math.max(10, alphaInactive / 2) << 24) | 0x00FFFFFF;
                                    break;
                            }

                            if (state == 1) { // Inactive
                                XposedHelpers.setLongField(colors, "background", ColorKt.Color(inactiveColorInt));
                            } else if (state == 2) { // Active
                                XposedHelpers.setLongField(colors, "background", ColorKt.Color(activeColorInt));
                            } else if (state == 0) { // Unavailable
                                XposedHelpers.setLongField(colors, "background", ColorKt.Color(unavailColorInt));
                            }
                        } catch (Throwable t) {
                            report("resolveTileContentColors override", t);
                        }
                    }
                });
                XposedBridge.log(TAG + ": TileContentColorsKt hooked");
            }
        } catch (Throwable t) {
            report("tile content colors hook", t);
        }
    }

    private static void hookScrimController(ClassLoader loader) {
        try {
            Class<?> scrimClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.ScrimController", loader);
            if (scrimClass == null) return;

            XposedBridge.hookAllConstructors(scrimClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        if (ctx != null) initObserver(ctx);
                    } catch (Throwable t) {
                        report("scrim constructor context lookup", t);
                    }
                }
            });

            XposedBridge.hookAllMethods(scrimClass, "updateScrimColor", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled) return;
                    try {
                        Object scrimBehind = XposedHelpers.getObjectField(param.thisObject, "mScrimBehind");
                        Object notifScrim = XposedHelpers.getObjectField(param.thisObject, "mNotificationsScrim");
                        if (param.args[0] == scrimBehind || param.args[0] == notifScrim) {
                            if (param.args[1] instanceof Float) {
                                float alpha = (Float) param.args[1];
                                if (alpha > 0.25f) {
                                    param.args[1] = 0.25f;
                                }
                            }
                            param.args[2] = COLOR_FROSTED_SCRIM;
                        }
                    } catch (Throwable t) {
                        report("updateScrimColor override", t);
                    }
                }
            });

            XposedBridge.hookAllMethods(scrimClass, "applyState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!enabled) return;
                    try {
                        float behindAlpha = XposedHelpers.getFloatField(param.thisObject, "mBehindAlpha");
                        if (behindAlpha > 0.25f) {
                            XposedHelpers.setFloatField(param.thisObject, "mBehindAlpha", 0.25f);
                        }
                        float notifAlpha = XposedHelpers.getFloatField(param.thisObject, "mNotificationsAlpha");
                        if (notifAlpha > 0.25f) {
                            XposedHelpers.setFloatField(param.thisObject, "mNotificationsAlpha", 0.25f);
                        }
                    } catch (Throwable t) {
                        report("applyState override", t);
                    }
                }
            });
            XposedBridge.log(TAG + ": ScrimController hooked");
        } catch (Throwable t) {
            report("scrim controller hook", t);
        }
    }

    private static void hookDepthController(ClassLoader loader) {
        try {
            Class<?> depthClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.NotificationShadeDepthController", loader);
            if (depthClass == null) return;

            XposedBridge.hookAllMethods(depthClass, "getNtOpaqueShadeCoversScreen", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (enabled) {
                        param.setResult(false);
                    }
                }
            });

            XposedBridge.hookAllMethods(depthClass, "shouldApplyShadeBlur", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (enabled) {
                        param.setResult(true);
                    }
                }
            });
            XposedBridge.log(TAG + ": NotificationShadeDepthController hooked");
        } catch (Throwable t) {
            report("depth controller hook", t);
        }
    }

    private static void hookResourceColors(ClassLoader loader) {
        try {
            Class<?> compatClass = XposedHelpers.findClassIfExists(
                    "androidx.core.content.res.ResourcesCompat", loader);
            if (compatClass != null) {
                XposedBridge.hookAllMethods(compatClass, "getColor", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!enabled || param.args.length < 2 || !(param.args[1] instanceof Integer)) return;
                        int id = (Integer) param.args[1];
                        if (param.args[0] instanceof Resources) {
                            resolveResourceIds((Resources) param.args[0]);
                        }
                        int replacement = getTranslucentColorForId(id);
                        if (replacement != 0) {
                            param.setResult(replacement);
                        }
                    }
                });
            }

            Class<?> resClass = XposedHelpers.findClassIfExists("android.content.res.Resources", loader);
            if (resClass != null) {
                XposedBridge.hookAllMethods(resClass, "getColor", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!enabled || param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                        int id = (Integer) param.args[0];
                        resolveResourceIds((Resources) param.thisObject);
                        int replacement = getTranslucentColorForId(id);
                        if (replacement != 0) {
                            param.setResult(replacement);
                        }
                    }
                });
            }
            XposedBridge.log(TAG + ": Color resource hooks registered");
        } catch (Throwable t) {
            report("resource colors hook", t);
        }
    }

    private static synchronized void resolveResourceIds(Resources res) {
        if (idQsTileInactiveBg != -1 || res == null) return;
        try {
            idQsTileInactiveBg = res.getIdentifier("qs_tile_inactive_background_color", "color", "com.android.systemui");
            idQsTileActiveBg = res.getIdentifier("qs_tile_active_background_color", "color", "com.android.systemui");
            idQsTileDisableBg = res.getIdentifier("qs_tile_disable_background_color", "color", "com.android.systemui");
            idQsTileUnavailBg = res.getIdentifier("qs_tile_unavailable_background_color", "color", "com.android.systemui");
            idQsTileMedUnavailBg = res.getIdentifier("qs_tile_medium_unavailable_background_color", "color", "com.android.systemui");
            idQsTileLargeUnavailBg = res.getIdentifier("qs_tile_large_unavailable_background_color", "color", "com.android.systemui");
            idQsTileOffBg = res.getIdentifier("qs_tile_off_background_color", "color", "com.android.systemui");
            idNtScrimBehind1 = res.getIdentifier("nt_scrim_behind_1", "color", "com.android.systemui");
            idNtScrimBehind2 = res.getIdentifier("nt_scrim_behind_2", "color", "com.android.systemui");
            idNtScrimBehindColor = res.getIdentifier("nt_scrim_behind_color", "color", "com.android.systemui");
            idNotifScrimBg = res.getIdentifier("notification_scrim_background_color", "color", "com.android.systemui");
        } catch (Throwable t) {
            report("resolving color resource ids", t);
        }
    }

    private static int getTranslucentColorForId(int id) {
        if (id <= 0) return 0;
        if (id == idQsTileInactiveBg) {
            return COLOR_FROSTED_INACTIVE;
        } else if (id == idQsTileDisableBg || id == idQsTileUnavailBg
                || id == idQsTileMedUnavailBg || id == idQsTileLargeUnavailBg || id == idQsTileOffBg) {
            return COLOR_FROSTED_UNAVAILABLE;
        } else if (id == idQsTileActiveBg) {
            return COLOR_FROSTED_ACTIVE;
        } else if (id == idNtScrimBehind1 || id == idNtScrimBehind2 || id == idNtScrimBehindColor) {
            return COLOR_FROSTED_SCRIM;
        } else if (id == idNotifScrimBg) {
            return COLOR_FROSTED_SCRIM;
        }
        return 0;
    }

    private static synchronized void report(String message, Throwable error) {
        if (REPORTED.add(message)) {
            XposedBridge.log(TAG + ": " + message + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
    }
}
