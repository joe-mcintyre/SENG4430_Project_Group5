package reports;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.domain.Finding;
import com.tool.domain.Severity;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;
import com.tool.reports.HTMLReportWriter;

public class HTMLReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void rendersProjectWideLabelForProjectLevelFindings() throws Exception {
        StubMetric metric = new StubMetric();
        Category category = new Category("Availability", "Availability metrics", new ArrayList<>(List.of(metric)));
        metric.setCategory(category);

        ArrayList<Finding> findings = new ArrayList<>();
        findings.add(new Finding(Severity.INFO, "Project summary", "project-wide", "summary", null));

        AuditResult auditResult = new AuditResult();
        auditResult.addResult(new MetricResult(metric, 0.0, findings, new ArrayList<>()));

        Path reportPath = tempDir.resolve("quality-report.html");
        new HTMLReportWriter(reportPath, tempDir).writeReport(auditResult);

        String html = Files.readString(reportPath);
        assertTrue(html.contains("Project Wide"));
        assertFalse(html.contains("<summary class='file-path-summary'>project-wide</summary>"));
    }

    @Test
    void rendersDropdownForPathsWithTrailingLineNumbers() throws Exception {
        StubMetric metric = new StubMetric();
        Category category = new Category("Maintainability", "Maintainability metrics", new ArrayList<>(List.of(metric)));
        metric.setCategory(category);

        Path findingPath = tempDir.resolve("src/main/java/com/example/Example.java");
        String reportPathValue = findingPath + ":42";

        ArrayList<Finding> findings = new ArrayList<>();
        findings.add(new Finding(Severity.INFO, "Method complexity", reportPathValue, "exampleMethod", 42));

        AuditResult auditResult = new AuditResult();
        auditResult.addResult(new MetricResult(metric, 0.0, findings, new ArrayList<>()));

        Path reportPath = tempDir.resolve("quality-report.html");
        new HTMLReportWriter(reportPath, tempDir).writeReport(auditResult);

        String html = Files.readString(reportPath);
        assertTrue(html.contains("<details class='file-path-details'>"));
        assertTrue(html.contains("<summary class='file-path-summary'>Example.java</summary>"));
        assertTrue(html.contains(Path.of("src", "main", "java", "com", "example", "Example.java").toString()));
        assertFalse(html.contains(reportPathValue));
    }

    private static final class StubMetric extends Metric {
        private StubMetric() {
            super(new ArrayList<>(), "Stub Metric", "Stub metric description");
        }

        @Override
        public MetricResult evaluate(Path projectPath) {
            throw new UnsupportedOperationException("Not used in this test");
        }
    }
}
