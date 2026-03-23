package com.tool.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tool.domain.Category;
import com.tool.metrics.MetricResult;

public class AuditResult {
    private final Map<Category, List<MetricResult>> results;

    public AuditResult() {
        this.results = new HashMap<>();
    }

    public void addResult(MetricResult result) {
        Category category = result.metric().category();
        results.computeIfAbsent(category, k -> new ArrayList<>())
               .add(result);
    }

    public List<MetricResult> resultsFor(Category category) {
        return results.getOrDefault(category, List.of());
    }

    public List<Category> categories() {
        return new ArrayList<>(results.keySet());
    }
}