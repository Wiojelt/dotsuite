package io.github.wiojelt.dotsuite.data

import java.text.Normalizer
import java.util.Locale

enum class FeatureArea(val title: String, val description: String) {
    SOUND("Sound", "Volume, mixing & media keys"),
    SHORTCUTS("Gestures", "Notch, dock & power button"),
    INTERFACE("Display", "Lock screen, motion & appearance"),
    STANDBY("StandBy", "Desk clock & your widgets"),
    CAMERA("Camera", "Capture shortcuts & Glyph countdown"),
    TOOLS("Tools", "Maps, sharing & quick settings"),
    SETTINGS("Settings", "Access, restore & app information"),
    SUPPORT("Coffee", "Buy me a coffee · optional support"),
}

data class FeatureEntry(val page: String, val title: String, val detail: String, val section: String, val terms: String = "") {
    val access: FeatureAccess get() = when (page) {
        "panel", "keys", "lockscreen", "notch", "carrier", "power", "share", "clock-style", "navigation", "back-arrow" -> FeatureAccess.ROOT_MODULE
        "apps", "hearing", "feedback", "motion", "display", "maps", "recovery", "clock", "rotation", "aod" -> FeatureAccess.BRIDGE
        "capture", "standby" -> FeatureAccess.PERMISSION
        "tiles" -> FeatureAccess.PER_TOOL
        else -> FeatureAccess.ON_DEVICE
    }
    val area: FeatureArea get() = when (section) {
        "Sound" -> FeatureArea.SOUND
        "Gestures" -> FeatureArea.SHORTCUTS
        "Display" -> FeatureArea.INTERFACE
        "StandBy" -> FeatureArea.STANDBY
        "Camera" -> FeatureArea.CAMERA
        "Tools" -> FeatureArea.TOOLS
        "Support" -> FeatureArea.SUPPORT
        else -> FeatureArea.SETTINGS
    }
}

enum class FeatureAccess(val label: String, val explanation: String) {
    ON_DEVICE("No root", "Works inside the app. Individual quick tiles may need their own access."),
    PERMISSION("Android permission", "Uses Android's normal permission or widget picker; root is not required."),
    PER_TOOL("Per tile", "Adding a tile needs no root. Each tile states the access needed to run its action."),
    BRIDGE("Shizuku / Sui", "Requires an authorised Shizuku or Sui connection. Root is not required with ADB-started Shizuku."),
    ROOT_MODULE("Root + module", "Requires Vector / LSPosed with the appropriate module scope, plus Shizuku / Sui to save settings. Root access alone does not confirm the hook is loaded."),
}

object FeatureVisibilityPolicy {
    fun showRoot(authorizedBridgeUid: Int?, requested: Boolean) = authorizedBridgeUid == 0 || requested
    fun visible(entry: FeatureEntry, showRoot: Boolean) = showRoot || entry.access != FeatureAccess.ROOT_MODULE
}

/** One catalog for routing and local search. Search text never leaves the app. */
object FeatureCatalog {
    val entries = listOf(
        FeatureEntry("appearance", "Appearance & play", "Translucent theme · portals · Russian roulette", "Maintenance", "tema rulet rus gorunum"),
        FeatureEntry("panel", "Volume panel", "Position · timing · native controls", "Sound", "ses paneli drawer"),
        FeatureEntry("apps", "App volume & mixing", "Individual levels · background audio", "Sound", "uygulama ses mix"),
        FeatureEntry("keys", "Volume keys", "Step size · screen-off track controls", "Sound", "ses tuslari medya"),
        FeatureEntry("hearing", "Hearing", "Mono audio · left / right balance", "Sound", "ses denge mono"),
        FeatureEntry("feedback", "Sounds & feedback", "Touch · lock · charging · dial pad", "Sound", "dokunma ses titresim sarj"),
        FeatureEntry("lockscreen", "Lock screen", "PIN privacy and keypad appearance", "Display", "kilit sifre"),
        FeatureEntry("notch", "Notch gestures", "Touch shortcuts · native status bar", "Gestures", "centik hareket gesture"),
        FeatureEntry("carrier", "Carrier label", "Your own display name · SIM unchanged", "Display", "operator isim"),
        FeatureEntry("motion", "Motion", "Animation timing · restore original values", "Display", "animasyon hiz sure"),
        FeatureEntry("display", "Extra dim", "Native dimming · original values saved", "Display", "ekran parlaklik"),
        FeatureEntry("clock", "Clock seconds", "Native status bar clock · no app timer", "Display", "saat saniye"),
        FeatureEntry("clock-style", "Weekday in clock", "Short day label · native time formatting", "Display", "saat gun tarih"),
        FeatureEntry("navigation", "Gesture indicator", "Hide the line · keep native gestures", "Display", "gezinme cubuk cizgi"),
        FeatureEntry("back-arrow", "System back arrow", "Native edge indicator · shapes & gesture motion", "Display", "geri oku ikon animasyon back gesture"),
        FeatureEntry("rotation", "Screen orientation", "Auto rotate · four directions · restore", "Display", "ekran donus yon"),
        FeatureEntry("capture", "Camera shortcuts", "Front / rear capture · visible recording status", "Camera", "kamera fotograf video kayit glyph"),
        FeatureEntry("maps", "Maps minimal mode", "Navigation shortcut · optional wake automation", "Tools", "harita navigasyon"),
        FeatureEntry("standby", "StandBy", "Desk clock · your installed Nothing widgets", "StandBy", "masa saati bekleme widget"),
        FeatureEntry("aod", "Always-on display", "Native AOD · wake gestures · notification pulses", "StandBy", "aod always on ambient ekran hep acik bekleme"),
        FeatureEntry("dock", "Quick dock", "App shortcuts · no edge gesture takeover", "Gestures", "yan cekmece sidedock"),
        FeatureEntry("power", "Power button", "Screen-off flashlight · native shortcuts first", "Gestures", "guc fener"),
        FeatureEntry("share", "Share sheet", "Hide suggested contacts · keep app targets", "Tools", "paylas cleanshare"),
        FeatureEntry("tiles", "Quick settings", "Add useful system tiles", "Tools", "hizli ayarlar qs"),
        FeatureEntry("recovery", "Restore changes", "Restore saved settings · preserve external changes", "Maintenance", "geri al reset varsayilan"),
        FeatureEntry("diagnostics", "Local diagnostics", "Recent changes · nothing uploaded", "Maintenance", "log hata kayit"),
        FeatureEntry("bug-report", "Report a bug", "Review the last minute · email a report", "Maintenance", "hata rapor email log bug report"),
        FeatureEntry("support", "Buy me a coffee", "Support independent development · entirely optional", "Support", "kahve destek wiojelt bagis"),
    )
    val routes = entries.map { it.page }.toSet()
    fun areaFor(page: String?) = entries.firstOrNull { it.page == page }?.area ?: FeatureArea.SETTINGS
    private fun normalized(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('ı', 'i')
    fun search(query: String): List<FeatureEntry> {
        val words = normalized(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.filter { entry ->
            val haystack = normalized("${entry.title} ${entry.detail} ${entry.section} ${entry.terms}")
            words.all { it in haystack }
        }
    }
}
