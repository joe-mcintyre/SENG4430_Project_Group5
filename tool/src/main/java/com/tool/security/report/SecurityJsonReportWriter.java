package com.tool.security.report;

import com.tool.security.model.SecurityFinding;
import com.tool.security.model.SecurityResult;
import com.tool.security.model.SecuritySeverity;
import com.tool.security.thresholds.SecurityThresholds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

public final class SecurityJsonReportWriter {

    public void write(Path output,
                      String projectName,
                      Path dependencyReport,
                      SecurityThresholds thresholds,
                      SecurityResult result) throws IOException {

        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = toJson(projectName, dependencyReport, thresholds, result);
        Files.writeString(output, json, StandardCharsets.UTF_8);
    }

    private String toJson(String projectName,
                          Path dependencyReport,
                          SecurityThresholds thresholds,
                          SecurityResult result) {

        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");

        appendField(sb, "project", projectName, 1, true);
        appendField(sb, "metric", "dependency_vulnerability_exposure", 1, true);
        appendField(sb, "generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString(), 1, true);

        sb.append(indent(1)).append("\"status\": \"")
                .append(result.passed() ? "PASS" : "FAIL")
                .append("\",\n");

        appendField(sb, "classification", result.classification(), 1, true);
        appendNumericField(sb, "score", result.score(), 1, true);

        sb.append(indent(1)).append("\"inputs\": {\n");
        appendField(sb, "dependencyReport", dependencyReport == null ? null : dependencyReport.toAbsolutePath().toString(), 2, false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"thresholds\": {\n");
        appendNumericField(sb, "maxCriticalAllowed", thresholds.maxCriticalAllowed(), 2, true);
        appendNumericField(sb, "maxHighAllowed", thresholds.maxHighAllowed(), 2, true);
        appendNumericField(sb, "minAcceptableScore", thresholds.minAcceptableScore(), 2, true);
        appendNumericField(sb, "minWarningScore", thresholds.minWarningScore(), 2, false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"results\": {\n");
        appendNumericField(sb, "totalDependenciesAnalysed", result.totalDependencies(), 2, true);
        appendNumericField(sb, "totalVulnerabilities", result.totalVulnerabilities(), 2, true);

        sb.append(indent(2)).append("\"countsBySeverity\": {\n");
        SecuritySeverity[] severities = SecuritySeverity.values();
        for (int i = 0; i < severities.length; i++) {
            SecuritySeverity s = severities[i];
            boolean comma = i < severities.length - 1;
            appendNumericField(sb, s.name(), result.count(s), 3, comma);
        }
        sb.append(indent(2)).append("},\n");

        appendNumericField(sb, "exploitableCount", result.exploitableCount(), 2, true);
        appendNumericField(sb, "patchAvailableCount", result.patchAvailableCount(), 2, true);
        appendNumericField(sb, "patchUnknownCount", result.patchUnknownCount(), 2, false);

        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"topVulnerabilities\": [\n");
        for (int i = 0; i < result.topFindings().size(); i++) {
            SecurityFinding f = result.topFindings().get(i);
            boolean comma = i < result.topFindings().size() - 1;

            sb.append(indent(2)).append("{\n");
            appendField(sb, "cveId", f.cveId(), 3, true);
            appendField(sb, "severity", f.severity().name(), 3, true);
            appendNumericField(sb, "cvss", f.cvssScore(), 3, true);
            appendField(sb, "dependency", f.dependency(), 3, true);
            appendField(sb, "description", f.description(), 3, true);
            if (f.patchAvailable() == null) {
                sb.append(indent(3)).append("\"patchAvailable\": null\n");
            } else {
                sb.append(indent(3)).append("\"patchAvailable\": ").append(f.patchAvailable()).append("\n");
            }
            sb.append(indent(2)).append("}").append(comma ? "," : "").append("\n");
        }
        sb.append(indent(1)).append("]\n");

        sb.append("}\n");
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String key, String value, int indent, boolean comma) {
        sb.append(this.indent(indent))
                .append('"').append(escapeJson(key)).append('"')
                .append(": ");

        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escapeJson(value)).append('"');
        }

        if (comma) sb.append(',');
        sb.append('\n');
    }

    private void appendNumericField(StringBuilder sb, String key, Number value, int indent, boolean comma) {
        sb.append(this.indent(indent))
                .append('"').append(escapeJson(key)).append('"')
                .append(": ");

        if (value == null) {
            sb.append("null");
        } else if (value instanceof Double d) {
            if (Double.isFinite(d)) {
                sb.append(String.format(Locale.US, "%.4f", d));
            } else {
                sb.append("null");
            }
        } else {
            sb.append(value);
        }

        if (comma) sb.append(',');
        sb.append('\n');
    }

    private String indent(int n) {
        return "  ".repeat(Math.max(0, n));
    }

    private String escapeJson(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
