package com.tool.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class ReliabilityThresholds {
    private final EnumMap<Severity, Integer> weights;
    private final double maxWeightedDensityPerKloc;
    private final int maxBlockerFindings;
    private final int maxCriticalFindings;
    private final double minKlocForNormalization;

    public ReliabilityThresholds(Map<Severity, Integer> weights,
                                 double maxWeightedDensityPerKloc,
                                 int maxBlockerFindings,
                                 int maxCriticalFindings,
                                 double minKlocForNormalization) {
        this.weights = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            this.weights.put(s, weights.getOrDefault(s, 0));
        }
        this.maxWeightedDensityPerKloc = maxWeightedDensityPerKloc;
        this.maxBlockerFindings = maxBlockerFindings;
        this.maxCriticalFindings = maxCriticalFindings;
        this.minKlocForNormalization = minKlocForNormalization;
    }

    public static ReliabilityThresholds defaults() {
        EnumMap<Severity, Integer> w = new EnumMap<>(Severity.class);
        w.put(Severity.BLOCKER, 20);
        w.put(Severity.CRITICAL, 10);
        w.put(Severity.MAJOR, 5);
        w.put(Severity.MINOR, 2);
        w.put(Severity.INFO, 0);

        return new ReliabilityThresholds(w,
                15.0,
                0,
                2,
                1.0);
    }

    public int weightFor(Severity severity) {
        return weights.getOrDefault(severity, 0);
    }

    public double maxWeightedDensityPerKloc() {
        return maxWeightedDensityPerKloc;
    }

    public int maxBlockerFindings() {
        return maxBlockerFindings;
    }

    public int maxCriticalFindings() {
        return maxCriticalFindings;
    }

    public double minKlocForNormalization() {
        return minKlocForNormalization;
    }

    public Map<Severity, Integer> weights() {
        return Collections.unmodifiableMap(weights);
    }
}
