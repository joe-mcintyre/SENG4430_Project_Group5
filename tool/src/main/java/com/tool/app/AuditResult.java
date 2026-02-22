package com.tool.app;

import java.util.ArrayList;
import java.util.List;

import com.tool.domain.Category;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

public class AuditResult {
    List<MetricResult> results;

    public AuditResult() {
        this.results = new ArrayList<>();
    }

    public void addResult(Category category, Metric metric, MetricResult result) {
        this.results.add(result);
    }

    public List<MetricResult> results() {
        return results;
    }
}