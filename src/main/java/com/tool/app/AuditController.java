package com.tool.app;

import java.nio.file.Files;
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
        return runAudit(projectPath, null);
    }

    public AuditResult runAudit(Path projectPath, Path dependencyReportPath) {
        if(projectPath == null) {
            throw new IllegalArgumentException("Project path cannot be null");
        }
        if (!Files.exists(projectPath)) {
            throw new IllegalArgumentException("Project path does not exist: " + projectPath);
        }

        AuditResult result = new AuditResult();
        try {
            for (Category category : categories) {
                for (Metric metric : category.metrics()) {
                    MetricResult res = metric.evaluate(projectPath, dependencyReportPath);
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
