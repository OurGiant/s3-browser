package com.ourgiant.s3.browser.model;

// Result of checking whether a profile's credentials are currently usable.
public class ProfileActivity {
    public final boolean active;
    public final String accountId;
    public final String errorMessage;
    public final String region;

    public ProfileActivity(boolean active, String accountId, String errorMessage, String region) {
        this.active = active;
        this.accountId = accountId;
        this.errorMessage = errorMessage;
        this.region = region;
    }
}
