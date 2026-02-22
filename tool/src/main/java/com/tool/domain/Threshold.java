package com.tool.domain;

public class Threshold {
    private Severity severity;
    private double threshold;

    public Threshold(Severity severity, double threshold) {
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
     * Check if the score is met
     * @return true if the score is met, false otherwise
     */
    public boolean isMet(double score) {
        return score >= threshold;
    }

    @Override
    public String toString() {
        return String.format("Thresholds{severity='%s', threshold=%.2f}", severity, threshold);
    }
}
