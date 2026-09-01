// IUserService.aidl
package io.github.wiojelt.dotsuite;

/**
 * Privileged interface implemented by {@code UserService}, which Shizuku runs in a
 * shell-uid (ADB) process. From that process the {@code appops} command is allowed to
 * change other apps' operation modes, which the app's own UID cannot do.
 */
interface IUserService {

    // Reserved transaction id required by the Shizuku server to tear down the service.
    void destroy() = 16777114;

    void exit() = 1;

    /**
     * Sets the TAKE_AUDIO_FOCUS app-op for {@code packageName} to one allow-listed mode. Ignored
     * apps no longer grab audio focus, so they stop pausing another player. Callers first read and
     * persist the original mode, then pass that exact mode when the switch is turned off.
     * Returns the raw command output for diagnostics.
     */
    String setAudioFocusMode(String packageName, String mode) = 2;

    /** Returns the raw {@code appops get} output for TAKE_AUDIO_FOCUS on the package. */
    String getAudioFocusMode(String packageName) = 3;

    /**
     * Active audio players, one entry per currently-playing stream, encoded as
     * "piid|uid|packageName". Uses hidden AudioManager/AudioPlaybackConfiguration APIs that are
     * only reachable from this shell-uid process. Empty list if none / on error.
     */
    List<String> getActivePlayers() = 4;

    /**
     * Sets the linear volume (0.0..1.0) of the player identified by {@code piid} via the hidden
     * PlayerProxy.setVolume API. Returns true on success.
     */
    boolean setPlayerVolume(int piid, float volume) = 5;

    /**
     * Whether any process for {@code packageName} is currently alive. Read from the shell-uid
     * process, which can see the full process table (the app's own UID cannot). Used to tell a
     * still-open app (screen off, backgrounded, paused) from one the user closed or force-stopped,
     * so a custom per-app volume survives the former but resets for the latter. Returns true when
     * the process table can't be read, so an unreadable {@code /proc} never wrongly discards a
     * live session.
     */
    boolean isPackageRunning(String packageName) = 6;

    /**
     * Sets the device ringer mode to {@code mode} ("NORMAL", "VIBRATE" or "SILENT") via
     * {@code cmd audio set-ringer-mode}, which lands on AudioService's internal setter — the same
     * one the system volume panel uses. That matters for SILENT: the app's own
     * {@code AudioManager.setRingerMode} is the *external* path, whose zen helper switches Do Not
     * Disturb on, while the internal one leaves DND completely alone. Returns the raw command
     * output; the sub-command only exists on newer platforms, so callers must handle failure.
     */
    String setRingerMode(String mode) = 7;

    /** Writes one allow-listed DotSuite feature flag to Settings.Secure. */
    String setFeatureEnabled(String key, boolean enabled) = 8;

    /** Writes one validated DotSuite preference to Settings.Secure. */
    String setSoundSetting(String key, int value) = 9;

    /** Validated carrier, motion, hearing, dimming and feedback options only. Null deletes one row. */
    String setSystemOption(String key, @nullable String value) = 10;

    /** Explicit, experimental Maps MinMode launch. No arbitrary component or command argument. */
    String launchMapsMinMode() = 11;

    /** JSON value/presence envelope for the same allow-list; private keys read only by the bridge. */
    String readSystemOption(String key) = 12;

    /** Read-only AOD capability flags and native schedule; never forces doze or a sensor. */
    String getAodCapabilities() = 13;

    /** User-confirmed reload of the exact SystemUI process; root/Sui only, never a reboot. */
    String reloadSystemUi() = 14;

    /** True while Android Auto currently owns audio focus. Read-only; no route is changed. */
    boolean isAndroidAutoActive() = 15;

}
