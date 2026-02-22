package com.tool.security.thresholds;

/**
 * Thresholds used to classify vulnerability exposure.
 */
public record SecurityThresholds(
        int maxCriticalAllowed,
        int maxHighAllowed,
        int minAcceptableScore,
        int minWarningScore
) {
    public static SecurityThresholds defaults() {
        return new SecurityThresholds(
                0,
                2,
                80,
                50
        );
    }
}
