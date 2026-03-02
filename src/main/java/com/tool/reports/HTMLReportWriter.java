package com.tool.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.domain.Finding;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

public class HTMLReportWriter extends ReportWriter {

    private StringBuilder htmlContent;

    public HTMLReportWriter(Path reportPath) {
        super(reportPath);
        this.htmlContent = new StringBuilder();
    }

    public void writeReport(AuditResult auditResult) throws Exception {

        writeHTMLContent(auditResult);

        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Quality Report</title>
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            background-color: #f4f6f9;
                            margin: 0;
                            padding: 40px;
                            color: #2c3e50;
                        }

                        .container {
                            max-width: 1100px;
                            margin: auto;
                            background: white;
                            padding: 40px;
                            border-radius: 4px;
                            box-shadow: 0 8px 30px rgba(0,0,0,0.08);
                        }

                        h1 {
                            margin-top: 0;
                            border-bottom: 2px solid #eee;
                            padding-bottom: 10px;
                        }

                        .category {
                            margin-top: 40px;
                        }

                        .category-title {
                            font-size: 22px;
                            margin-bottom: 5px;
                        }

                        .category-description {
                            color: #6c757d;
                            margin-bottom: 20px;
                        }

                        .metric-card {
                            border: 1px solid #e5e7eb;
                            border-radius: 4px;
                            padding: 20px;
                            margin-bottom: 20px;
                            background: #fafafa;
                        }

                        .metric-header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            margin-bottom: 10px;
                        }

                        .metric-name {
                            font-size: 18px;
                            font-weight: 600;
                        }

                        .badge {
                            padding: 6px 12px;
                            border-radius: 4px;
                            font-size: 12px;
                            font-weight: 600;
                        }

                        .badge-score {
                            background-color: #0d6efd;
                            color: #fdecea;
                        }

                        .badge-critical {
                            background-color: #d32f2f;
                            color: #fdecea;
                        }

                        .badge-major {
                            background-color: #d34d2f;
                            color: #fdecea;
                        }

                        .badge-minor {
                            background-color: #d3cb2f;
                            color: #fdecea;
                        }

                        .badge-info {
                            background-color: #2fd3c8;
                            color: #fdecea;
                        }

                        .badge-success {
                            background-color: #2fd334;
                            color: #fdecea;fdecea
                        }

                        table {
                            width: 100%;
                            border-collapse: collapse;
                            margin-top: 15px;
                        }

                        th, td {
                            text-align: left;
                            padding: 8px;
                            border-bottom: 1px solid #eee;
                            font-size: 14px;
                        }

                        th {
                            background-color: #f8f9fa;
                            font-weight: 600;
                        }

                        .empty {
                            text-align: center;
                            padding: 40px;
                            color: #6c757d;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>Software Quality Audit Report</h1>
                """);

        html.append(htmlContent);

        html.append("""
                    </div>
                </body>
                </html>
                """);

        if (reportPath.getParent() != null) {
            Files.createDirectories(reportPath.getParent());
        }

        Files.writeString(reportPath, html.toString());
    }

    private void writeHTMLContent(AuditResult auditResult) {

        List<MetricResult> results = auditResult.results();

        if (results.isEmpty()) {
            htmlContent.append("<div class='empty'><h2>No Results Found</h2></div>");
            return;
        }

        Category currentCategory = null;

        for (MetricResult metricResult : results) {

            Metric metric = metricResult.metric();

            if (!metric.category().equals(currentCategory)) {
                currentCategory = metric.category();

                htmlContent.append("<div class='category'>");
                htmlContent.append("<div class='category-title'>")
                           .append(currentCategory.name())
                           .append("</div>");
                htmlContent.append("<div class='category-description'>")
                           .append(currentCategory.description())
                           .append("</div>");
                htmlContent.append("</div>");
            }

            htmlContent.append("<div class='metric-card'>");

            htmlContent.append("<div class='metric-header'>");
            htmlContent.append("<div class='metric-name'>")
                       .append(metric.name())
                       .append("</div>");

            htmlContent.append("<div>");
            htmlContent.append("<span class='badge badge-score'>Score: ")
                       .append(metricResult.score())
                       .append("</span> ");

            Threshold highestThreshold = metricResult.mostSevereThreshold();
            String badgeClass = highestThreshold == null? "success" : highestThreshold.toString();
            String badgeText = highestThreshold == null? 
                                "Success": "Highest Severity: " + metricResult.mostSevereThreshold().toString();

            htmlContent.append("<span class='badge badge-"+badgeClass+"'>")
                       .append(badgeText)
                       .append("</span>");
            htmlContent.append("</div>");

            htmlContent.append("</div>");

            htmlContent.append("<p>")
                       .append(metric.description())
                       .append("</p>");

            if (!metricResult.findings().isEmpty()) {

                htmlContent.append("<table>");
                htmlContent.append("<tr>")
                           .append("<th>File</th>")
                           .append("<th>Line</th>")
                           .append("<th>Function</th>")
                           .append("<th>Message</th>")
                           .append("</tr>");

                for (Finding finding : metricResult.findings()) {
                    htmlContent.append("<tr>");
                    htmlContent.append("<td>").append(finding.file()).append("</td>");
                    htmlContent.append("<td>").append(finding.line()).append("</td>");
                    htmlContent.append("<td>").append(finding.function()).append("</td>");
                    htmlContent.append("<td>").append(finding.message()).append("</td>");
                    htmlContent.append("</tr>");
                }

                htmlContent.append("</table>");
            }

            htmlContent.append("</div>");
        }
    }
}