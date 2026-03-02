package com.tool.metrics;

import java.nio.file.Path;
import java.util.ArrayList;

import com.tool.domain.Threshold;

/**
 * Abstract class representing a metric that can be evaluated on a project. 
 * Each metric has a name, description, and a list of thresholds that determine 
 * the severity of the findings based on the score.
 */
public abstract class Metric {
    private final ArrayList<Threshold> thresholds; 
    private final String name;
    private final String description;

    /**
     * Create a new metric with the given thresholds, name, and description.
     * @param thresholds the thresholds for this metric, ordered from highest severity to lowest severity
     * @param name the name of the metric
     * @param description a description of the metric
     */
    public Metric(ArrayList<Threshold> thresholds, String name, String description) {
        if(thresholds == null) {
            throw new IllegalArgumentException("Thresholds cannot be null");
        } else if(name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        } else if(description == null || description.isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        
        // Sort by threshold severity, so the highest severity is first. 
        this.thresholds = new ArrayList<>(thresholds);
        this.name = name;
        this.description = description;
    }

    public String name(){
        return name;
    }

    public String description(){
        return description;
    }

    public ArrayList<Threshold> thresholds() {
        return thresholds;
    }

    /**
     * Evaluate the metric for the given project path and store the score and findings internally.
     * @param projectPath the path to the project being audited
     * @return a MetricResult containing the score and findings for this metric
     */
    public abstract MetricResult evaluate(Path projectPath) throws Exception;;
}
