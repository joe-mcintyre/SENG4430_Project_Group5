package com.tool.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ReliabilityResult {
    private final EnumMap<Severity, Integer> countsBySeverity;
    private final int totalFindings;
    private final int weightedPoints;
    private final double effectiveKloc;
    private final double weightedDensityPerKloc;
    private final boolean passed;
    private final String grade;
    private final List<Finding> topFindings;

    public ReliabilityResult(Map<Severity, Integer> countsBySeverity,
                             int totalFindings,
                             int weightedPoints,
                             double effectiveKloc,
                             double weightedDensityPerKloc,
                             boolean passed,
                             String grade,
                             List<Finding> topFindings) {
        this.countsBySeverity = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            this.countsBySeverity.put(s, countsBySeverity.getOrDefault(s, 0));
        }

        this.totalFindings = totalFindings;
        this.weightedPoints = weightedPoints;
        this.effectiveKloc = effectiveKloc;
        this.weightedDensityPerKloc = weightedDensityPerKloc;
        this.passed = passed;
        this.grade = grade;
        this.topFindings = List.copyOf(topFindings);
    }

    public int count(Severity severity) {
        return countsBySeverity.getOrDefault(severity, 0);
    }

    public Map<Severity, Integer> countsBySeverity() {
        return Collections.unmodifiableMap(countsBySeverity);
    }

    public int totalFindings() {
        return totalFindings;
    }

    public int weightedPoints() {
        return weightedPoints;
    }

    public double effectiveKloc() {
        return effectiveKloc;
    }

    public double weightedDensityPerKloc() {
        return weightedDensityPerKloc;
    }

    public boolean passed() {
        return passed;
    }

    public String grade() {
        return grade;
    }

    public List<Finding> topFindings() {
        return Collections.unmodifiableList(topFindings);
    }
}
