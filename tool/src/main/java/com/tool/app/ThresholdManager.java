package com.tool.app;

import com.tool.domain.ReliabilityThresholds;
import com.tool.domain.Severity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Properties;

public class ThresholdManager {

    public ReliabilityThresholds load(Path path) throws IOException {
        ReliabilityThresholds defaults = ReliabilityThresholds.defaults();

        if (path == null || !Files.exists(path)) {
            return defaults;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        }

        EnumMap<Severity, Integer> weights = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            String key = "reliability.weight." + severity.name();
            int value = parseInt(properties, key, defaults.weightFor(severity));
            weights.put(severity, value);
        }

        double maxDensity = parseDouble(properties,
                "reliability.maxWeightedDensityPerKloc",
                defaults.maxWeightedDensityPerKloc());

        int maxBlocker = parseInt(properties,
                "reliability.maxBlockerFindings",
                defaults.maxBlockerFindings());

        int maxCritical = parseInt(properties,
                "reliability.maxCriticalFindings",
                defaults.maxCriticalFindings());

        double minKloc = parseDouble(properties,
                "reliability.minKlocForNormalization",
                defaults.minKlocForNormalization());

        return new ReliabilityThresholds(weights, maxDensity, maxBlocker, maxCritical, minKloc);
    }

    private int parseInt(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double parseDouble(Properties p, String key, double fallback) {
        String raw = p.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
