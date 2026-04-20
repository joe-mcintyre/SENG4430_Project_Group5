package metrics.availability.portability_tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import com.tool.metrics.MetricResult;

// These lean more end-to-end and check the stuff a real run can trip over.
public class PortabilityPassRateMetricSystemTest extends PortabilityMetricTestSupport {

    @Test
    void reportsInfrastructureFailuresWhenACommandCannotStart() throws Exception {
        MetricResult result = newMetric(new JSONArray()
            .put(localTarget("missing-command", "supported", 1.0, "definitely-not-a-real-executable"))
        ).evaluate(tempDir);

        assertEquals(0.0, result.score(), 0.0001);
        assertTrue(result.findings().stream().anyMatch(f -> f.message().contains("INFRASTRUCTURE FAILED")));
    }

    @Test
    void reportsTimeoutsAsCompatibilityFailures() throws Exception {
        // The probe just sleeps here so we can force a timeout without doing anything weird.
        MetricResult result = newMetric(new JSONArray()
            .put(probeTarget("slow-target", "supported", 1.0, 1, "sleep", "3000"))
        ).evaluate(tempDir);

        assertEquals(1.0, result.score(), 0.0001);
        assertTrue(result.findings().stream().anyMatch(f -> f.message().contains("COMPATIBILITY FAILED (timeout)")));
    }

    @Test
    void reportsNonZeroExitCodesAsCompatibilityFailures() throws Exception {
        MetricResult result = newMetric(new JSONArray()
            .put(localTarget("bad-java-invocation", "supported", 1.0, javaExecutable(), "--definitely-invalid-option"))
        ).evaluate(tempDir);

        assertEquals(1.0, result.score(), 0.0001);
        assertTrue(result.findings().stream().anyMatch(f -> f.message().contains("COMPATIBILITY FAILED (exit-code)")));
    }
}
