package com.tool.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.domain.Finding;
import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;
import com.tool.metrics.availability.PortabilityPassRateMetric;
import com.tool.util.ResourceUtil;

public class HTMLReportWriter extends ReportWriter {
    private static final String PROJECT_WIDE_FILE = "project-wide";
    private static final String PROJECT_WIDE_LABEL = "Project Wide";

    private final StringBuilder htmlContent;
    private final Path projectRoot;

    public HTMLReportWriter(Path reportPath) {
        this(reportPath, null);
    }

    public HTMLReportWriter(Path reportPath, Path projectRoot) {
        super(reportPath);
        this.htmlContent = new StringBuilder();
        this.projectRoot = projectRoot;
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
                    <title>Audit Report — Automotive</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@400;600;700;800&family=Barlow:wght@400;500;600&family=Share+Tech+Mono&display=swap" rel="stylesheet">
                    <link rel="stylesheet" href="quality-report.css">
                </head>
                <body>

                    <div class="topbar">
                        <div class="topbar-brand">
                            <div class="topbar-logo"></div>
                            <span class="topbar-title">Software Quality Audit System</span>
                        </div>
                    </div>

                    <div class="layout">
                        <nav class="sidebar" id="sidebar">
                            <div class="sidebar-label">Inspection Modules</div>
                """);

        html.append(htmlContent);

        html.append("""
                    </div>

                    <a href="#" class="go-top" id="goTop" aria-label="Back to top">&#8593;</a>

                    <script>
                        const btn = document.getElementById('goTop');
                        window.addEventListener('scroll', () => {
                            btn.classList.toggle('visible', window.scrollY > 300);
                        });
                        btn.addEventListener('click', e => {
                            e.preventDefault();
                            window.scrollTo({ top: 0, behavior: 'smooth' });
                        });
                    </script>
                </body>
                </html>
                """);

        if (reportPath.getParent() != null) {
            Files.createDirectories(reportPath.getParent());
        }

        Files.writeString(reportPath, html.toString());

        // Copy css
        Path cssPath = reportPath.getParent().resolve("quality-report.css");
        Files.copy(
            ResourceUtil.getResourcePath("HTMLReport.css"),
            cssPath,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
}

    private void writeHTMLContent(AuditResult auditResult) {
        List<Category> categories = auditResult.categories();

        if (categories.isEmpty()) {
            writeEmptyState();
            return;
        }

        writeSidebarLinks(categories);
        htmlContent.append("</nav><main class='main'>");
        writePageHeader();
        writeCategorySections(auditResult, categories);
        htmlContent.append("</main>");
    }

    private void writeEmptyState() {
        htmlContent.append("</nav><main class='main'>");
        htmlContent.append("<div class='empty'>No project data</div>");
        htmlContent.append("</main>");
    }

    private void writeSidebarLinks(List<Category> categories) {
        int idx = 0;
        for (Category category : categories) {
            idx++;
            htmlContent.append("<a href='#cat-")
                    .append(categoryAnchor(category))
                    .append("' class='sidebar-link'>")
                    .append(String.format("%02d", idx))
                    .append(" &nbsp;")
                    .append(escapeHtml(category.name()))
                    .append("</a>");
        }
    }

    private void writePageHeader() {
        htmlContent.append("""
                <div class='page-header'>
                    <div class='page-eyebrow'>Diagnostic Report</div>
                    <div class='page-title'>Software Quality<br><span>Audit Report</span></div>
                </div>
                """);
    }

    private void writeCategorySections(AuditResult auditResult, List<Category> categories) {
        int catIdx = 0;
        for (Category category : categories) {
            if (catIdx != 0) {
                htmlContent.append("</div>");
            }
            catIdx++;
            writeCategorySection(auditResult, category, catIdx);
        }
        htmlContent.append("</div>");
    }

    private void writeCategorySection(AuditResult auditResult, Category category, int catIdx) {
        htmlContent.append("<div class='category-section'>");
        writeCategoryHeader(category, catIdx);
        htmlContent.append("<div class='category-desc'>")
                .append(escapeHtml(category.description()))
                .append("</div>");

        for (MetricResult metricResult : auditResult.resultsFor(category)) {
            writeMetricCard(metricResult);
        }
    }

    private void writeCategoryHeader(Category category, int catIdx) {
        htmlContent.append("<div class='category-header' id='cat-")
                .append(categoryAnchor(category))
                .append("'>");
        htmlContent.append("<span class='category-number'>")
                .append(String.format("%02d", catIdx))
                .append("</span>");
        htmlContent.append("<span class='category-title'>")
                .append(escapeHtml(category.name()))
                .append("</span>");
        htmlContent.append("<div class='category-rule'></div>");
        htmlContent.append("</div>");
    }

    private void writeMetricCard(MetricResult metricResult) {
        Metric metric = metricResult.metric();
        Threshold highestThreshold = metricResult.mostSevereThreshold();
        String sevClass = severityClass(highestThreshold);

        htmlContent.append("<div class='metric-card ").append(sevClass).append("'>");
        writeMetricHeader(metric, metricResult, highestThreshold);
        htmlContent.append("<p class='metric-description'>")
                .append(escapeHtml(metric.description()))
                .append("</p>");

        writeMetricThresholds(metricResult);

        if (!metricResult.findings().isEmpty()) {
            writeFindingsTable(metricResult.findings());
        }

        htmlContent.append("</div>");
    }

    private void writeMetricHeader(Metric metric, MetricResult metricResult, Threshold highestThreshold) {
        htmlContent.append("<div class='metric-header'>");
        htmlContent.append("<div class='metric-name'>")
                .append(escapeHtml(metric.name()))
                .append("</div>");
        writeMetricBadges(metric, metricResult, highestThreshold);
        htmlContent.append("</div>");
    }

    private void writeMetricBadges(Metric metric, MetricResult metricResult, Threshold highestThreshold) {
        String badgeClass = highestThreshold == null ? "success" : highestThreshold.toString().toLowerCase();
        String badgeText  = highestThreshold == null ? "Pass" : highestThreshold.toString();

        htmlContent.append("<div class='badge-group'>");
        htmlContent.append("<span class='badge badge-score'>")
                .append(formatScoreLabel(metric))
                .append(": ")
                .append(formatScore(metricResult))
                .append("</span>");
        htmlContent.append("<span class='badge badge-").append(badgeClass).append("'>")
                .append(escapeHtml(badgeText))
                .append("</span>");
        htmlContent.append("</div>");
    }

    private void writeMetricThresholds(MetricResult metricResult) {
        ArrayList<Threshold> thresholds = metricResult.metric().thresholds();
        if (thresholds.isEmpty()) return;

        htmlContent.append("<div class='metric-thresholds'>");
        htmlContent.append("<span class='threshold-label'>Thresholds</span>");

        for (Threshold threshold : thresholds) {
            String sevKey = threshold.toString().toLowerCase();
            htmlContent.append("<span class='threshold-item sev-").append(sevKey).append("'>")
                    .append("<span class='threshold-sev'>").append(escapeHtml(threshold.toString())).append("</span>")
                    .append("<span class='threshold-sep'>&#x2265;</span>")
                    .append("<span class='threshold-val'>").append(threshold.value()).append("</span>")
                    .append("</span>");
        }

        htmlContent.append("</div>");
    }

    private void writeFindingsTable(List<Finding> findings) {
        List<Finding> sorted = findings.stream()
                .sorted(Comparator.comparingInt(f -> f.severity().ordinal()))
                .collect(Collectors.toList());

        htmlContent.append("<div class='findings-table-wrap'>");
        htmlContent.append("<table>");
        htmlContent.append("<thead><tr>")
                .append("<th>Severity</th>")
                .append("<th>Source File</th>")
                .append("<th>Line</th>")
                .append("<th>Function / Method</th>")
                .append("<th>Diagnostic Message</th>")
                .append("</tr></thead>");
        htmlContent.append("<tbody>");

        for (Finding finding : sorted) {
            writeFindingRow(finding);
        }

        htmlContent.append("</tbody></table></div>");
    }

    private void writeFindingRow(Finding finding) {
        htmlContent.append("<tr>");
        htmlContent.append("<td>")
                .append(writeSeverityBadge(finding.severity(), finding.severity().toString()))
                .append("</td>");
        htmlContent.append("<td style='position:relative'>")
                .append(renderFileCell(finding.file()))
                .append("</td>");
        htmlContent.append("<td>")
                .append(escapeHtml(safeValue(finding.line())))
                .append("</td>");
        htmlContent.append("<td>")
                .append(escapeHtml(safeValue(finding.function())))
                .append("</td>");
        htmlContent.append("<td>")
                .append(escapeHtml(safeValue(finding.message())))
                .append("</td>");
        htmlContent.append("</tr>");
    }

    private String renderFileCell(String rawPath) {
        if (isProjectWideFile(rawPath)) {
            return PROJECT_WIDE_LABEL;
        }

        if (rawPath == null || rawPath.isBlank()) {
            return "N/A";
        }

        String normalizedPath = stripLocationSuffix(rawPath);
        String fileName = getFileName(normalizedPath);
        String relativePath = getRelativePath(normalizedPath);

        String escapedFileName = escapeHtml(fileName);
        String escapedRelativePath = escapeHtml(relativePath);

        if (fileName.equals(relativePath)) {
            return escapedFileName;
        }

        return "<details class='file-path-details'>"
                + "<summary class='file-path-summary'>" + escapedFileName + "</summary>"
                + "<code class='file-path-full'>" + escapedRelativePath + "</code>"
                + "</details>";
    }

    private boolean isProjectWideFile(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }

        String trimmedPath = stripLocationSuffix(rawPath).trim();
        if (PROJECT_WIDE_FILE.equalsIgnoreCase(trimmedPath)) {
            return true;
        }

        if (projectRoot == null) {
            return false;
        }

        try {
            return Path.of(trimmedPath).toAbsolutePath().normalize()
                    .equals(projectRoot.toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    private String stripLocationSuffix(String rawPath) {
        if (rawPath == null) {
            return null;
        }

        return rawPath.trim().replaceFirst(":(\\d+)(?::\\d+)?$", "");
    }

    private String getFileName(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "N/A";
        }

        try {
            Path filePath = Path.of(rawPath);
            Path fileName = filePath.getFileName();
            return fileName != null ? fileName.toString() : rawPath;
        } catch (Exception e) {
            return rawPath;
        }
    }

    private String getRelativePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "N/A";
        }

        try {
            Path filePath = Path.of(rawPath).normalize();

            if (projectRoot != null) {
                Path root = projectRoot.toAbsolutePath().normalize();
                Path absolutePath = filePath.isAbsolute()
                        ? filePath.toAbsolutePath().normalize()
                        : root.resolve(filePath).normalize();

                try {
                    return root.relativize(absolutePath).toString();
                } catch (Exception ignored) {
                }
            }

            return filePath.toString();
        } catch (Exception e) {
            return rawPath;
        }
    }

    private String safeValue(Object value) {
        if (value == null) {
            return "N/A";
        }

        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? "N/A" : text;
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String categoryAnchor(Category category) {
        return category.name().replace(" ", "-");
    }

    private String writeSeverityBadge(Severity severity, String label) {
        return "<span class='badge badge-" + severity.toString().toLowerCase() + "'>"
                + escapeHtml(label)
                + "</span>";
    }

    private String severityClass(Threshold threshold) {
        return threshold == null
                ? "sev-success"
                : "sev-" + threshold.toString().toLowerCase();
    }

    private String formatScoreLabel(Metric metric) {
        if (metric instanceof PortabilityPassRateMetric) {
            return "Pass Rate";
        }
        return "Score";
    }

    private String formatScore(MetricResult metricResult) {
        if (metricResult.metric() instanceof PortabilityPassRateMetric) {
            double passRate = Math.max(0.0, Math.min(1.0, 1.0 - metricResult.score()));
            return String.format("%.2f%%", passRate * 100.0);
        }
        return String.format("%.2f", metricResult.score());
    }
}
