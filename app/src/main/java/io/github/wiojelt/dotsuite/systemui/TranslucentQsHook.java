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
 * Translucent Quick Settings, High-Contrast Frosted Glass Tiles & Scrim Hook.
 * Inspired by iOS Control Center, OneUI QuickStar, and Nothing OS Cyberpunk aesthetics.
 * Solves washed out / unreadable text with dynamic smoked glass scrim and inverted tile typography.
 */
final class TranslucentQsHook {
    private static final String TAG = "DotSuite.TranslucentQs";
    private static final Set<String> REPORTED = new HashSet<>();
    private static boolean installed;
    private static volatile boolean enabled = true;
    private static volatile int qsTransparency = 50; // 0..100
    private static volatile int qsTileStyle = 1; // 0: Stock, 1: Frosted Glass, 2: Liquid Glass, 3: Nothing Red, 4: Obsidian
    private static volatile int qsScrimDarkening = 65; // 10..95 (%) dark scrim for high contrast
    private static volatile int qsTextContrastMode = 0; // 0: Auto WCAG AAA, 1: Inverted Black on Active, 2: Pure Minimal White
    private static ContentResolver contentResolver;
    private static ContentObserver contentObserver;

    // High contrast smoked frosted glass colors
    private static final int COLOR_OBSIDIAN_PANEL = 0x88121214;       // Smoked dark translucent glass
    private static final int COLOR_FROSTED_ACTIVE_WHITE = 0xFFFFFFFF; // Pure crisp white
    private static final int COLOR_TEXT_DARK = 0xFF111111;            // AAA contrast on white
    private static final int COLOR_TEXT_LIGHT = 0xFFFFFFFF;           // Crisp white
    private static final int COLOR_TEXT_MUTED = 0xB3FFFFFF;           // 70% white
    private static final int COLOR_TEXT_SUBTLE = 0xFF333333;          // Subtle dark for secondary label

    // Resource IDs
    private static int idQsTileInactiveBg = -1;
    private static int idQsTileActiveBg = -1;
    private static int idQsTileDisableBg = -1;
    private static int idQsTileUnavailBg = -1;
    private static int idQsTileMedUnavailBg = -1;
    private static int idQsTileLargeUnavailBg = -1;
    private static int idQsTileOffBg = -1;
    private static int idQsTileLabelActive = -1;
    private static int idQsTileLabelInactive = -1;
    private static int idQsTileIconActive = -1;
    private static int idQsTileIconInactive = -1;
    private static int idQsTileSecondaryLabelInactive = -1;
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
            updatePreferences();

            contentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    updatePreferences();
                    XposedBridge.log(TAG + ": updated: enabled=" + enabled + ", transparency=" + qsTransparency
                            + ", style=" + qsTileStyle + ", darkening=" + qsScrimDarkening);
                }
            };
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.TRANSLUCENT_QS), false, contentObserver);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.QS_TRANSPARENCY), false, contentObserver);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.QS_TILE_STYLE), false, contentObserver);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.QS_SCRIM_DARKENING), false, contentObserver);
            contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(PersonalizationPolicy.QS_TEXT_CONTRAST_MODE), false, contentObserver);
            XposedBridge.log(TAG + ": observer registered, initialState=" + enabled);
        } catch (Throwable t) {
            report("failed to init observer", t);
        }
    }

    private static void updatePreferences() {
        if (contentResolver == null) return;
        try {
            enabled = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.TRANSLUCENT_QS, 1) == 1;
            qsTransparency = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_TRANSPARENCY, 50);
            qsTileStyle = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_TILE_STYLE, 1);
            qsScrimDarkening = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_SCRIM_DARKENING, 65);
            qsTextContrastMode = Settings.Secure.getInt(contentResolver, PersonalizationPolicy.QS_TEXT_CONTRAST_MODE, 0);
        } catch (Throwable t) {
            report("updatePreferences", t);
        }
    }

    private static int getDynamicScrimColor() {
        int alpha = Math.max(25, Math.min(242, (int) (qsScrimDarkening * 2.55f)));
        return (alpha << 24) | 0x000000;
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
                            param.setResult(getDynamicScrimColor());
                        }
                    }
                });

                XposedBridge.hookAllMethods(shadeColorsClass, "shadePanel", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (enabled) {
                            int panelAlpha = Math.max(30, (int) ((100 - qsTransparency) * 1.8f));
                            param.setResult((panelAlpha << 24) | 0x141416);
                        }
                    }
                });

                XposedBridge.hookAllMethods(shadeColorsClass, "notificationScrim", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (enabled) {
                            param.setResult(getDynamicScrimColor());
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

                            int activeBg;
                            int inactiveBg;
                            int unavailBg;
                            int activeLabel = COLOR_TEXT_DARK;
                            int activeIcon = COLOR_TEXT_DARK;
                            int activeSecondary = COLOR_TEXT_SUBTLE;

                            int alphaInactive = Math.max(30, Math.min(180, (int) (qsTransparency * 1.5f)));

                            switch (qsTileStyle) {
                                case 2: // Liquid Glass (Cyan tint & glow)
                                    inactiveBg = (alphaInactive << 24) | 0x00CAE8;
                                    activeBg = 0xFF00E5FF;
                                    unavailBg = (Math.max(15, alphaInactive / 2) << 24) | 0x00CAE8;
                                    activeLabel = 0xFF051820;
                                    activeIcon = 0xFF051820;
                                    activeSecondary = 0xFF1A3844;
                                    break;
                                case 3: // Nothing Red Dot Accent
                                    inactiveBg = (alphaInactive << 24) | 0x222224;
                                    activeBg = 0xFFD71921;
                                    unavailBg = (Math.max(15, alphaInactive / 2) << 24) | 0x222224;
                                    activeLabel = COLOR_TEXT_LIGHT;
                                    activeIcon = COLOR_TEXT_LIGHT;
                                    activeSecondary = 0xCCFFFFFF;
                                    break;
                                case 4: // Obsidian High-Contrast Dark
                                    inactiveBg = (Math.max(40, alphaInactive) << 24) | 0x242428;
                                    activeBg = 0xFFFFFFFF;
                                    unavailBg = (Math.max(15, alphaInactive / 2) << 24) | 0x1E1E22;
                                    activeLabel = COLOR_TEXT_DARK;
                                    activeIcon = COLOR_TEXT_DARK;
                                    activeSecondary = COLOR_TEXT_SUBTLE;
                                    break;
                                default: // Frosted Glass (Default)
                                    inactiveBg = (alphaInactive << 24) | 0xFFFFFF;
                                    activeBg = COLOR_FROSTED_ACTIVE_WHITE;
                                    unavailBg = (Math.max(15, alphaInactive / 2) << 24) | 0xFFFFFF;
                                    activeLabel = COLOR_TEXT_DARK;
                                    activeIcon = COLOR_TEXT_DARK;
                                    activeSecondary = COLOR_TEXT_SUBTLE;
                                    break;
                            }

                            if (state == 2) { // Active
                                XposedHelpers.setLongField(colors, "background", ColorKt.Color(activeBg));
                                XposedHelpers.setLongField(colors, "labelColor", ColorKt.Color(activeLabel));
                                XposedHelpers.setLongField(colors, "iconColor", ColorKt.Color(activeIcon));
                                XposedHelpers.setLongField(colors, "secondaryLabelColor", ColorKt.Color(activeSecondary));
                            } else if (state == 1) { // Inactive
                                XposedHelpers.setLongField(colors, "background", ColorKt.Color(inactiveBg));
                                XposedHelpers.setLongField(colors, "labelColor", ColorKt.Color(COLOR_TEXT_LIGHT));
                                XposedHelpers.setLongField(colors, "iconColor", ColorKt.Color(COLOR_TEXT_LIGHT));
                                XposedHelpers.setLongField(colors, "secondaryLabelColor", ColorKt.Color(COLOR_TEXT_MUTED));
                            } else if (state == 0) { // Unavailable
                                XposedHelpers.setLongField(colors, "background", ColorKt.Color(unavailBg));
                                XposedHelpers.setLongField(colors, "labelColor", ColorKt.Color(0x77FFFFFF));
                                XposedHelpers.setLongField(colors, "iconColor", ColorKt.Color(0x77FFFFFF));
                                XposedHelpers.setLongField(colors, "secondaryLabelColor", ColorKt.Color(0x4DFFFFFF));
                            }
                        } catch (Throwable t) {
                            report("resolveTileContentColors override", t);
                        }
                    }
                });
                XposedBridge.log(TAG + ": TileContentColorsKt high contrast typography hooked");
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
                            float maxAllowedAlpha = Math.max(0.40f, qsScrimDarkening / 100.0f);
                            if (param.args[1] instanceof Float) {
                                float alpha = (Float) param.args[1];
                                if (alpha > maxAllowedAlpha) {
                                    param.args[1] = maxAllowedAlpha;
                                }
                            }
                            param.args[2] = getDynamicScrimColor();
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
                        float targetAlpha = Math.max(0.40f, qsScrimDarkening / 100.0f);
                        float behindAlpha = XposedHelpers.getFloatField(param.thisObject, "mBehindAlpha");
                        if (behindAlpha > targetAlpha) {
                            XposedHelpers.setFloatField(param.thisObject, "mBehindAlpha", targetAlpha);
                        }
                        float notifAlpha = XposedHelpers.getFloatField(param.thisObject, "mNotificationsAlpha");
                        if (notifAlpha > targetAlpha) {
                            XposedHelpers.setFloatField(param.thisObject, "mNotificationsAlpha", targetAlpha);
                        }
                    } catch (Throwable t) {
                        report("applyState override", t);
                    }
                }
            });
            XposedBridge.log(TAG + ": ScrimController hooked with dynamic smoked scrim");
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
            idQsTileLabelActive = res.getIdentifier("qs_tile_label_active_color", "color", "com.android.systemui");
            idQsTileLabelInactive = res.getIdentifier("qs_tile_label_inactive_color", "color", "com.android.systemui");
            idQsTileIconActive = res.getIdentifier("qs_tile_icon_active_color", "color", "com.android.systemui");
            idQsTileIconInactive = res.getIdentifier("qs_tile_icon_inactive_color", "color", "com.android.systemui");
            idQsTileSecondaryLabelInactive = res.getIdentifier("qs_tile_secondary_label_inactive_color", "color", "com.android.systemui");
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
        int scrim = getDynamicScrimColor();
        int alphaInactive = Math.max(30, Math.min(180, (int) (qsTransparency * 1.5f)));

        if (id == idQsTileInactiveBg) {
            return (alphaInactive << 24) | 0xFFFFFF;
        } else if (id == idQsTileActiveBg) {
            return COLOR_FROSTED_ACTIVE_WHITE;
        } else if (id == idQsTileLabelActive || id == idQsTileIconActive) {
            return COLOR_TEXT_DARK;
        } else if (id == idQsTileLabelInactive || id == idQsTileIconInactive) {
            return COLOR_TEXT_LIGHT;
        } else if (id == idQsTileSecondaryLabelInactive) {
            return COLOR_TEXT_MUTED;
        } else if (id == idQsTileDisableBg || id == idQsTileUnavailBg
                || id == idQsTileMedUnavailBg || id == idQsTileLargeUnavailBg || id == idQsTileOffBg) {
            return (Math.max(15, alphaInactive / 2) << 24) | 0xFFFFFF;
        } else if (id == idNtScrimBehind1 || id == idNtScrimBehind2 || id == idNtScrimBehindColor || id == idNotifScrimBg) {
            return scrim;
        }
        return 0;
    }

    private static synchronized void report(String message, Throwable error) {
        if (REPORTED.add(message)) {
            XposedBridge.log(TAG + ": " + message + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
        }
    }
}
