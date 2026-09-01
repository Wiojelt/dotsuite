package io.github.wiojelt.dotsuite.data;

/** Parse a one-key SettingsProvider query without confusing "null" with no row. */
public final class SettingQueryResult {
    public final boolean valid;
    public final String value;
    private SettingQueryResult(boolean valid, String value) { this.valid = valid; this.value = value; }
    public static SettingQueryResult parse(String output) {
        if ("No result found.".equals(output)) return new SettingQueryResult(true, null);
        String prefix = "Row: 0 value=";
        if (output != null && output.startsWith(prefix) && output.indexOf('\n') < 0 && output.indexOf('\r') < 0)
            return new SettingQueryResult(true, output.substring(prefix.length()));
        return new SettingQueryResult(false, null);
    }
}
