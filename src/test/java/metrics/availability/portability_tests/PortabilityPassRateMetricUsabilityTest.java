package metrics.availability.portability_tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.metrics.MetricResult;
import com.tool.metrics.availability.PortabilityPassRateMetric;
import com.tool.reports.HTMLReportWriter;

// These are less about raw mechanics and more about whether the output is actually understandable.
public class PortabilityPassRateMetricUsabilityTest extends PortabilityMetricTestSupport {

    @Test
    void findingsExplainTheOutcomeAndWhetherItAffectedScoring() throws Exception {
        // If someone reads the finding later, they should be able to tell what happened without guessing.
        MetricResult result = newMetric(new JSONArray()
            .put(localTarget("supported-pass", "supported", 1.0, javaExecutable(), "-version"))
            .put(localTarget("experimental-fail", "experimental", 1.0, javaExecutable(), "--definitely-invalid-option"))
        ).evaluate(tempDir);

        assertTrue(result.findings().stream().anyMatch(f ->
            f.message().contains("Target 'supported-pass' PASSED")
                && f.message().contains("counted in weighted risk")
        ));
        assertTrue(result.findings().stream().anyMatch(f ->
            f.message().contains("Target 'experimental-fail' COMPATIBILITY FAILED")
                && f.message().contains("excluded from weighted risk")
        ));
    }

    @Test
    void htmlReportShowsPortabilityAsAPassRate() throws Exception {
        // The report flips risk into pass rate, so this checks the number that an actual user would see.
        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(localTarget("supported-pass", "supported", 0.70, javaExecutable(), "-version"))
            .put(localTarget("supported-fail", "supported", 0.30, javaExecutable(), "--definitely-invalid-option"))
        );

        Category category = new Category(
            "Availability",
            "Checks portability",
            new ArrayList<>(List.of(metric))
        );
        metric.setCategory(category);

        MetricResult result = metric.evaluate(tempDir);
        AuditResult auditResult = new AuditResult();
        auditResult.addResult(result);

        Path reportPath = tempDir.resolve("portability-report.html");
        new HTMLReportWriter(reportPath).writeReport(auditResult);

        String html = Files.readString(reportPath);
        assertTrue(html.contains("Pass Rate"));
        assertTrue(html.contains("70.00%"));
    }
}
