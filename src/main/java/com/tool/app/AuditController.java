package com.tool.app;

import java.nio.file.Path;
import java.util.ArrayList;

import com.tool.domain.Category;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;
import com.tool.util.ConfigLoader;

public class AuditController {
    private final ArrayList<Category> categories;

    public AuditController(Path configPath) {
        if(configPath == null) {
            throw new IllegalArgumentException("Config path cannot be null");
        }

        this.categories = ConfigLoader.loadCategories(configPath);
    }

    public AuditResult runAudit(Path projectPath) {
        if(projectPath == null) {
            throw new IllegalArgumentException("Project path cannot be null");
        } 

        AuditResult result = new AuditResult();
        try {
            // Iterate through each category and metric, evaluate the metric, and store the results
            for (Category category : categories) {
                for (Metric metric : category.metrics()) {
                    MetricResult res = metric.evaluate(projectPath);
                    result.addResult(res);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run audit: " + e.getMessage(), e);
        }
     
        return result;
    }

    @Override
    public String toString() {
        return String.format("CategoryManager{categories=%s}", categories.toString());
    }
}
