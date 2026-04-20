package reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.domain.Finding;
import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;
import com.tool.reports.HTMLReportWriter;
import com.tool.reports.JSONReportWriter;

class ReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void htmlWriterShowsAnEmptyStateWhenNoResultsExist() throws Exception {
        Path reportPath = tempDir.resolve("empty-report.html");
        HTMLReportWriter writer = new HTMLReportWriter(reportPath);

        writer.writeReport(new AuditResult());

        String html = Files.readString(reportPath);
        assertTrue(html.contains("No project data"));
    }

    @Test
    void htmlWriterIncludesCategoryMetricAndFindingDetails() throws Exception {
        Path reportPath = tempDir.resolve("audit-report.html");
        HTMLReportWriter writer = new HTMLReportWriter(reportPath);

        writer.writeReport(sampleAuditResult());

        String html = Files.readString(reportPath);
        assertTrue(html.contains("Security"));
        assertTrue(html.contains("Manual Check"));
        assertTrue(html.contains("One issue found"));
    }

    @Test
    void jsonWriterKeepsMetricAndFindingFieldsInTheOutput() throws Exception {
        Path reportPath = tempDir.resolve("audit-report.json");
        JSONReportWriter writer = new JSONReportWriter(reportPath);

        writer.writeReport(sampleAuditResult());

        JSONObject root = new JSONObject(Files.readString(reportPath));
        JSONObject category = root.getJSONArray("categories").getJSONObject(0);
        JSONObject metric = category.getJSONArray("metrics").getJSONObject(0);
        JSONObject finding = metric.getJSONArray("findings").getJSONObject(0);

        assertEquals("Security", category.getString("name"));
        assertEquals("Manual Check", metric.getString("metric"));
        assertEquals("One issue found", finding.getString("message"));
        assertEquals("run", finding.getString("function"));
    }

    private AuditResult sampleAuditResult() {
        ArrayList<Threshold> thresholds = new ArrayList<>();
        thresholds.add(new Threshold(Severity.MAJOR, 1.0));

        StubMetric metric = new StubMetric(thresholds, "Manual Check", "Simple metric used for report checks.");
        ArrayList<Metric> metrics = new ArrayList<>();
        metrics.add(metric);

        Category category = new Category("Security", "A small category for report writer tests.", metrics);
        metric.setCategory(category);

        ArrayList<Finding> findings = new ArrayList<>();
        findings.add(new Finding(Severity.MAJOR, "One issue found", "src/Demo.java", "run", 12));

        MetricResult result = new MetricResult(metric, 1.5, findings, thresholds);
        AuditResult auditResult = new AuditResult();
        auditResult.addResult(result);
        return auditResult;
    }

    private static final class StubMetric extends Metric {
        private StubMetric(ArrayList<Threshold> thresholds, String name, String description) {
            super(thresholds, name, description);
        }

        @Override
        public MetricResult evaluate(Path projectPath) {
            return new MetricResult(this, 0.0, new ArrayList<>(), thresholds());
        }
    }
}
