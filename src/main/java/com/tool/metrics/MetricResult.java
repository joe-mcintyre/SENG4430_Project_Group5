package com.tool.metrics;

import java.util.ArrayList;


import com.tool.domain.Finding;
import com.tool.domain.Threshold;

/**
 * Represents the result of evaluating a metric, including the score, the most severe threshold that was met, 
 * and any findings associated with the metric evaluation.
 */
public class MetricResult {
    private final double score;
    private final Threshold mostSevereThreshold;
    private final ArrayList<Finding> findings;

    public MetricResult(double score, ArrayList<Finding> findings, ArrayList<Threshold> thresholds) {
        this.score = score;
        this.findings = findings;
        this.mostSevereThreshold = evaluateSeverity(thresholds);
    }

    public double score() {
        return score;
    }

    public ArrayList<Finding> findings() {
        return findings;
    }

    public Threshold mostSevereThreshold() {
        return mostSevereThreshold;
    }

    /**
     * Evaluate the severity of the metric result based on the provided thresholds. 
     * @param thresholds the thresholds to evaluate against, ordered from highest severity to lowest severity
     * @return The most severe threshold that is met will be returned. If no thresholds are met, null will be returned.
     */
    private Threshold evaluateSeverity(ArrayList<Threshold> thresholds) {
        for (Threshold threshold : thresholds) {
            if (threshold.isMet(score)) {
                return threshold;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Score: ").append(score).append("\n");
        sb.append("Most Severe Threshold Met: ").append(mostSevereThreshold != null ? mostSevereThreshold.severity() : "None").append("\n");
        sb.append("Findings:\n");
        for (Finding finding : findings) {
            sb.append("- ").append(finding.toString()).append("\n");
        }
        return sb.toString();
    }
}
