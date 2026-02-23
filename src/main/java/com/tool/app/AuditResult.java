package com.tool.app;

import java.util.ArrayList;
import java.util.List;

import com.tool.metrics.MetricResult;

public class AuditResult {
    private final List<MetricResult> results;

    public AuditResult() {
        this.results = new ArrayList<>();
    }

    public void addResult(MetricResult result) {
        this.results.add(result);
    }

    public List<MetricResult> results() {
        return results;
    }
}