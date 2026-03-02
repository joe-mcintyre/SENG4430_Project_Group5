package com.tool.domain;

import java.util.Arrays;
import java.util.List;

public class Threshold implements Comparable<Threshold> {
    private final Severity severity;
    private final double value;

    public static final List<String> THRESHOLD_REGISTRY = Arrays.asList("blocker", "critical", "major", "minor", "info");


    public Threshold(Severity severity, double value) {
        if (severity == null) {
            throw new IllegalArgumentException("Severity cannot be null");
        }
        this.severity = severity;
        this.value = value;
    }   

    public Severity severity() {
        return severity;
    }

    public double value() {
        return value;
    }

    /**
     * Check if this threshold is met for the given score.
     * @param score the score to evaluate against the threshold in the range [0, 1]
     * @return true if the score meets or exceeds the threshold, false otherwise
     */
    public boolean isMet(double score) {
        return score <= value;
    }

    @Override
    public int compareTo(Threshold other) {
        return Double.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return severity.toString();
    }
}
