package com.ourgiant.s3.browser.core;

import java.util.ArrayList;
import java.util.List;

public final class ConnectionMessages {

    private ConnectionMessages() {
    }

    // Builds the main window title from the current connection, so it's obvious at a glance
    // which profile/account/region is connected (e.g. to avoid confusing prod and QA).
    // Degrades gracefully when the account ID isn't known yet.
    public static String windowTitle(String connectedProfile, String connectedAccountId, String connectedRegion) {
        StringBuilder title = new StringBuilder("S3 Browser");

        if (connectedProfile != null && !connectedProfile.isEmpty()) {
            title.append(" — ").append(connectedProfile);

            List<String> details = new ArrayList<>();
            if (connectedAccountId != null && !connectedAccountId.isEmpty()) {
                details.add(connectedAccountId);
            }
            if (connectedRegion != null && !connectedRegion.isEmpty()) {
                details.add(connectedRegion);
            }
            if (!details.isEmpty()) {
                title.append(" (").append(String.join(", ", details)).append(")");
            }
        }

        return title.toString();
    }
}
