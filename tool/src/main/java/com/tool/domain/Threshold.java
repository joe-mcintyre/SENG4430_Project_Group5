package com.tool.domain;

public class Threshold {
    private final Severity severity;
    private final double threshold;

    public Threshold(Severity severity, double threshold) {
        if (severity == null) {
            throw new IllegalArgumentException("Severity cannot be null");
        } else if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Threshold must be between 0 and 1");
        }

        this.severity = severity;
        this.threshold = threshold;
    }   

    public Severity severity() {
        return severity;
    }

    public double threshold() {
        return threshold;
    }

    /**
     * Check if this threshold is met for the given score.
     * @param score the score to evaluate against the threshold in the range [0, 1]
     * @return true if the score meets or exceeds the threshold, false otherwise
     */
    public boolean isMet(double score) {
        return score <= threshold;
    }

    @Override
    public String toString() {
        return String.format("Threshold{severity='%s', threshold=%.2f}", severity, threshold);
    }
}
