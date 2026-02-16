package com.tool.reports;

import com.tool.app.ProjectContext;
import com.tool.domain.Finding;
import com.tool.domain.LocStats;
import com.tool.domain.ReliabilityResult;
import com.tool.domain.ReliabilityThresholds;
import com.tool.domain.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

public class JsonReportWriter {

    public void write(Path output,
                      ProjectContext project,
                      LocStats locStats,
                      ReliabilityThresholds thresholds,
                      ReliabilityResult result) throws IOException {

        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String json = toJson(project, locStats, thresholds, result);
        Files.writeString(output, json, StandardCharsets.UTF_8);
    }

    private String toJson(ProjectContext project,
                          LocStats locStats,
                          ReliabilityThresholds thresholds,
                          ReliabilityResult result) {

        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        appendField(sb, "project", project.projectName(), 1, true);
        appendField(sb, "metric", "weighted_reliability_findings_density", 1, true);
        appendField(sb, "generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString(), 1, true);

        sb.append(indent(1)).append("\"status\": \"")
                .append(result.passed() ? "PASS" : "FAIL")
                .append("\",\n");

        appendField(sb, "grade", result.grade(), 1, true);

        sb.append(indent(1)).append("\"inputs\": {\n");
        appendField(sb, "sourceRoot", project.sourceRoot().toAbsolutePath().toString(), 2, true);
        appendField(sb, "spotBugsReport", project.spotbugsReport().toAbsolutePath().toString(), 2, false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"thresholds\": {\n");
        appendNumericField(sb, "maxWeightedDensityPerKloc", thresholds.maxWeightedDensityPerKloc(), 2, true);
        appendNumericField(sb, "maxBlockerFindings", thresholds.maxBlockerFindings(), 2, true);
        appendNumericField(sb, "maxCriticalFindings", thresholds.maxCriticalFindings(), 2, true);
        appendNumericField(sb, "minKlocForNormalization", thresholds.minKlocForNormalization(), 2, true);

        sb.append(indent(2)).append("\"weights\": {\n");
        int sIdx = 0;
        Severity[] severities = Severity.values();
        for (Severity s : severities) {
            boolean comma = sIdx < severities.length - 1;
            appendNumericField(sb, s.name(), thresholds.weightFor(s), 3, comma);
            sIdx++;
        }
        sb.append(indent(2)).append("}\n");
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"results\": {\n");
        appendNumericField(sb, "javaFileCount", locStats.javaFileCount(), 2, true);
        appendNumericField(sb, "logicalLoc", locStats.logicalLoc(), 2, true);
        appendNumericField(sb, "rawKloc", locStats.kloc(), 2, true);
        appendNumericField(sb, "effectiveKloc", result.effectiveKloc(), 2, true);
        appendNumericField(sb, "totalFindings", result.totalFindings(), 2, true);

        sb.append(indent(2)).append("\"countsBySeverity\": {\n");
        for (int i = 0; i < severities.length; i++) {
            Severity s = severities[i];
            boolean comma = i < severities.length - 1;
            appendNumericField(sb, s.name(), result.count(s), 3, comma);
        }
        sb.append(indent(2)).append("},\n");

        appendNumericField(sb, "weightedPoints", result.weightedPoints(), 2, true);
        appendNumericField(sb, "weightedDensityPerKloc", result.weightedDensityPerKloc(), 2, false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"topFindings\": [\n");
        for (int i = 0; i < result.topFindings().size(); i++) {
            Finding f = result.topFindings().get(i);
            boolean comma = i < result.topFindings().size() - 1;

            sb.append(indent(2)).append("{\n");
            appendField(sb, "tool", f.tool(), 3, true);
            appendField(sb, "ruleId", f.ruleId(), 3, true);
            appendField(sb, "severity", f.severity().name(), 3, true);
            appendField(sb, "file", f.file(), 3, true);
            if (f.line() == null) {
                sb.append(indent(3)).append("\"line\": null,\n");
            } else {
                appendNumericField(sb, "line", f.line(), 3, true);
            }
            appendField(sb, "message", f.message(), 3, false);
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
