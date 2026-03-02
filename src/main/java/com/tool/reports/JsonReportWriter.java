package com.tool.reports;

import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tool.domain.Finding;
import com.tool.metrics.MetricResult;

public class JsonReportWriter implements ReportWriter {
    private final JSONObject root;
    private final JSONArray results;
    private final Path reportPath;

    public JsonReportWriter(Path reportPath) {
        this.reportPath = reportPath;

        this.root = new JSONObject();
        this.results = new JSONArray();

        root.put("results", results);
    }

    @Override
    public void appendMetadata(String metadata) {
        root.put("metadata", metadata);
    }

    @Override
    public void appendResult(MetricResult result) {
        JSONObject resultObj = new JSONObject();

        resultObj.put("score", result.score());

        if (result.mostSevereThreshold() != null) {
            resultObj.put("severity", result.mostSevereThreshold().severity().toString());
        } else {
            resultObj.put("severity", JSONObject.NULL);
        }

        JSONArray findingsArray = new JSONArray();

        for (Finding finding : result.findings()) {
            JSONObject findingObj = new JSONObject();

            findingObj.put("severity", finding.severity().toString());
            findingObj.put("message", finding.message());
            findingObj.put("file", finding.file());
            findingObj.put("line", finding.line());

            findingsArray.put(findingObj);
        }

        resultObj.put("findings", findingsArray);

        results.put(resultObj);
    }

    public void writeReport() throws Exception {
        if (reportPath.getParent() != null) {
            Files.createDirectories(reportPath.getParent());
        }

        Files.writeString(
                reportPath,
                root.toString(2)
        );
    }
}
