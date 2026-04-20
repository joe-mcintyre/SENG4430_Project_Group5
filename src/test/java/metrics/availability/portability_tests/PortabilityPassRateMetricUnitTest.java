package metrics.availability.portability_tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.tool.metrics.MetricResult;
import com.tool.metrics.availability.PortabilityPassRateMetric;

// These are the small, direct checks so the basic rules stay locked in.
public class PortabilityPassRateMetricUnitTest extends PortabilityMetricTestSupport {

    @Test
    void rejectsNullSettings() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new PortabilityPassRateMetric(defaultThresholds(), null)
        );
    }

    @Test
    void rejectsAnEmptyTargetList() {
        JSONObject settings = defaultSettings(new JSONArray());

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> new PortabilityPassRateMetric(defaultThresholds(), settings)
        );

        assertTrue(error.getMessage().contains("at least one target"));
    }

    @Test
    void calculatesWeightedRiskWithoutExperimentalTargets() throws Exception {
        // Experimental targets can still fail, they just should not drag the main score down.
        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(localTarget("supported-pass", "supported", 0.70, javaExecutable(), "-version"))
            .put(localTarget("supported-fail", "supported", 0.30, javaExecutable(), "--definitely-invalid-option"))
            .put(localTarget("experimental-fail", "experimental", 1.00, javaExecutable(), "--definitely-invalid-option"))
        );

        MetricResult result = metric.evaluate(tempDir);

        assertEquals(0.30, result.score(), 0.0001);
        assertEquals("major", result.mostSevereThreshold().toString());
    }

    @Test
    void canTreatSkippedTargetsAsFailuresWhenConfigured() throws Exception {
        // This covers the stricter scoring mode where a skipped target still counts against you.
        JSONObject settings = defaultSettings(new JSONArray()
            .put(localTarget("host-pass", "supported", 1.0, javaExecutable(), "-version"))
            .put(localTarget("other-os", "supported", 1.0, javaExecutable(), "-version")
                .put("os", new JSONArray().put(differentOs())))
        ).put("count_skipped_as_failure", true);

        MetricResult result = newMetric(settings).evaluate(tempDir);

        assertEquals(0.50, result.score(), 0.0001);
        assertTrue(result.findings().stream().anyMatch(f -> f.message().contains("Target 'other-os' skipped")));
    }
}
