package io.github.wiojelt.dotsuite.i18n

/** Only the copy used by the single DotSuite configuration screen. */
class Strings {
    val dismiss = "Dismiss"
    val install = "Install"
    val tryAgain = "Try again"

    val mixingSearchApps = "Search apps"
    val mixingHideSystemApps = "Hide system apps"
    fun mixingCouldntUpdate(app: String) = "Couldn't update $app"
    val mixingWarning =
        "Selected apps may keep playing together. Restore them before Android Auto or another " +
            "external audio session so Android can negotiate its normal route and focus."

    val setupIntroShizuku =
        "Audio mixing uses Shizuku / Sui. Finish these three steps to enable it."
    fun setupStep(number: Int, title: String) = "$number. $title"
    val setupShizukuInstalled = "Shizuku installed"
    val setupShizukuNotInstalled = "Shizuku not installed"
    val setupShizukuInstallDetail =
        "It hosts the privileged helper that changes audio focus."
    val setupServiceRunning = "Shizuku service running"
    val setupServiceNotRunning = "Shizuku service not running"
    val setupServiceStartDetail =
        "Start it from Shizuku using wireless debugging or ADB."
    val setupServerUnusableDetail =
        "An older Shizuku service is still running. Stop it, then start Shizuku again."
    val setupRestartShizuku = "Restart Shizuku"
    val setupSetUpNow = "Set up now"
    val setupAccessGranted = "Access granted to DotSuite"
    val setupGrantAccessTitle = "Grant access to DotSuite"
    val setupAccessDetail = "Allow per-app audio focus control."
    val setupConnectFailedDetail = "Shizuku couldn't start the privileged helper."
    val setupConnectingDetail = "Starting the privileged helper…"
    val setupGrantAccess = "Grant access"
}
