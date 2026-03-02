package com.tool.domain;

import java.util.ArrayList;

import com.tool.metrics.Metric;

public class Category {
    private final String name;
    private final String description;
    private final ArrayList<Metric> metrics;

    public Category(String name, String description, ArrayList<Metric> metrics) {
        if(name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        } else if(description == null || description.isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        } else if(metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("Metrics cannot be null or empty");
        }

        this.name = name;
        this.description = description;
        this.metrics = metrics;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ArrayList<Metric> metrics() {
        return metrics;
    }

    @Override
    public String toString() {
        return String.format("Category{name='%s', description='%s', metrics=%s}", name, description, metrics.toString());
    }
}
