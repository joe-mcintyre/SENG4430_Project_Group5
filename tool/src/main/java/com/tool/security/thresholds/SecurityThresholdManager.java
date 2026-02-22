package com.tool.security.thresholds;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads security thresholds from the shared properties file.
 *
 * Keys (all optional; defaults used if missing):
 *   security.maxCriticalAllowed
 *   security.maxHighAllowed
 *   security.minAcceptableScore
 *   security.minWarningScore
 */
public final class SecurityThresholdManager {

    public SecurityThresholds load(Path propertiesPath) throws IOException {
        SecurityThresholds defaults = SecurityThresholds.defaults();
        if (propertiesPath == null || !Files.exists(propertiesPath)) {
            return defaults;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(propertiesPath)) {
            properties.load(in);
        }

        int maxCritical = parseInt(properties, "security.maxCriticalAllowed", defaults.maxCriticalAllowed());
        int maxHigh = parseInt(properties, "security.maxHighAllowed", defaults.maxHighAllowed());
        int minAccept = parseInt(properties, "security.minAcceptableScore", defaults.minAcceptableScore());
        int minWarn = parseInt(properties, "security.minWarningScore", defaults.minWarningScore());

        return new SecurityThresholds(maxCritical, maxHigh, minAccept, minWarn);
    }

    private int parseInt(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
