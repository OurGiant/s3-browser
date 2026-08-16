package com.ourgiant.s3.browser;

import java.util.prefs.Preferences;

/**
 * Local app state: last-connected AWS profile/region, last-notified update version.
 * Backed by {@link Preferences} since this app's own state is small enough that a
 * database would be overkill.
 */
public class AppPreferences {

    private static final String KEY_AWS_PROFILE = "awsProfile";
    private static final String KEY_AWS_REGION = "awsRegion";
    private static final String KEY_LAST_NOTIFIED_UPDATE_VERSION = "lastNotifiedUpdateVersion";

    private final Preferences prefs;

    public AppPreferences() {
        this.prefs = Preferences.userRoot().node("/com/ourgiant/s3/browser/gui");
    }

    public String getAwsProfile(String defaultValue) {
        return prefs.get(KEY_AWS_PROFILE, defaultValue);
    }

    public void setAwsProfile(String awsProfile) {
        prefs.put(KEY_AWS_PROFILE, awsProfile);
    }

    public String getAwsRegion(String defaultValue) {
        return prefs.get(KEY_AWS_REGION, defaultValue);
    }

    public void setAwsRegion(String awsRegion) {
        prefs.put(KEY_AWS_REGION, awsRegion);
    }

    /**
     * The version the silent startup update check last auto-opened the About box for, so it
     * doesn't nag on every single launch while a known update sits unapplied -- once per new
     * version, not once per launch. Null if never notified.
     */
    public String getLastNotifiedUpdateVersion() {
        return prefs.get(KEY_LAST_NOTIFIED_UPDATE_VERSION, null);
    }

    public void setLastNotifiedUpdateVersion(String version) {
        prefs.put(KEY_LAST_NOTIFIED_UPDATE_VERSION, version);
    }
}
