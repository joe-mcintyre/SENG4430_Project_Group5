package com.tool.reports;

import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.metrics.MetricResult;

public class JSONReportWriter extends ReportWriter {

    public JSONReportWriter(Path reportPath){
        super(reportPath);
    }

    @Override
    public void writeReport(AuditResult auditResult) throws Exception {
        JSONObject root = new JSONObject();
        JSONArray categoriesArray = new JSONArray();

        for (Category category : auditResult.categories()) {
            categoriesArray.put(categoryToJSON(auditResult, category));
        }

        root.put("categories", categoriesArray);

        Files.writeString(reportPath, root.toString(4));
    }

    private JSONObject categoryToJSON(AuditResult auditResult, Category category) {
        JSONObject categoryJson = new JSONObject();

        categoryJson.put("name", category.name());
        categoryJson.put("description", category.description());

        JSONArray metricsArray = new JSONArray();
        for (MetricResult result : auditResult.resultsFor(category)) {
            metricsArray.put(resultToJSON(result));
        }

        categoryJson.put("metrics", metricsArray);

        return categoryJson;
    }

    private JSONObject resultToJSON(MetricResult result) {
        JSONObject json = new JSONObject();

        json.put("metric", result.metric().name());
        json.put("description", result.metric().description());
        json.put("score", result.score());

        // Threshold
        if (result.mostSevereThreshold() != null) {
            JSONObject thresholdJson = new JSONObject();
            thresholdJson.put("severity", result.mostSevereThreshold().severity());
            json.put("threshold", thresholdJson);
        } else {
            json.put("threshold", JSONObject.NULL);
        }

        // Findings
        JSONArray findingsArray = new JSONArray();
        for (var finding : result.findings()) {
            JSONObject findingJson = new JSONObject();

            findingJson.put("message", finding.message());
            findingJson.put("file", finding.file());
            findingJson.put("function", finding.function());
            findingJson.put("line", finding.line());

            findingsArray.put(findingJson);
        }

        json.put("findings", findingsArray);

        return json;
    }
}