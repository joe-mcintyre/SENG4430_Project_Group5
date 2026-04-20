package metrics.availability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tool.domain.Threshold;
import com.tool.metrics.MetricResult;
import com.tool.metrics.availability.PortabilityPassRateMetric;
import com.tool.util.ConfigLoader;

public class PortabilityPassRateMetricTest {

    @TempDir
    Path tempDir;

    @Test
    void excludesExperimentalFailuresFromWeightedRisk() throws Exception {
        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(localTarget("supported-pass", "supported", 0.70, javaExecutable(), "-version"))
            .put(localTarget("supported-fail", "supported", 0.30, javaExecutable(), "--definitely-invalid-option"))
            .put(localTarget("experimental-fail", "experimental", 1.00, javaExecutable(), "--definitely-invalid-option"))
        );

        MetricResult result = metric.evaluate(tempDir);

        assertEquals(0.30, result.score(), 0.0001);
        assertNotNull(result.mostSevereThreshold());
        assertEquals("major", result.mostSevereThreshold().toString());
    }

    @Test
    void excludesInfrastructureFailuresFromWeightedRisk() throws Exception {
        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(localTarget("supported-pass", "supported", 1.00, javaExecutable(), "-version"))
            .put(localTarget("infra-fail", "supported", 1.00, "definitely-not-a-real-executable"))
        );

        MetricResult result = metric.evaluate(tempDir);

        assertEquals(0.0, result.score(), 0.0001);
        assertTrue(result.findings().stream().anyMatch(f -> f.message().contains("INFRASTRUCTURE FAILED")));
    }

    @Test
    void reportsAvailabilityFindingsAsProjectWide() throws Exception {
        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(localTarget("supported-pass", "supported", 1.00, javaExecutable(), "-version"))
        );

        MetricResult result = metric.evaluate(tempDir);

        assertFalse(result.findings().isEmpty());
        assertTrue(result.findings().stream().allMatch(f -> "project-wide".equals(f.file())));
    }

    @Test
    void isolatesWorkspacePerTarget() throws Exception {
        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(localTarget(
                "write-marker",
                "supported",
                0.0,
                javaExecutable(),
                "-cp",
                testClassPath(),
                PortabilityTargetProbe.class.getName(),
                "write",
                "marker.txt"
            ))
            .put(localTarget(
                "assert-marker-missing",
                "supported",
                1.0,
                javaExecutable(),
                "-cp",
                testClassPath(),
                PortabilityTargetProbe.class.getName(),
                "assert-missing",
                "marker.txt"
            ))
        );

        MetricResult result = metric.evaluate(tempDir);

        assertEquals(0.0, result.score(), 0.0001);
        assertFalse(Files.exists(tempDir.resolve("marker.txt")));
    }

    @Test
    void resolvesExecutionRootFromNestedSourcePath() throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(localTarget(
                "check-pom",
                "supported",
                1.0,
                javaExecutable(),
                "-cp",
                testClassPath(),
                PortabilityTargetProbe.class.getName(),
                "assert-exists",
                "pom.xml"
            ))
        );

        MetricResult result = metric.evaluate(tempDir.resolve("src/main/java"));

        assertEquals(0.0, result.score(), 0.0001);
    }

    private PortabilityPassRateMetric newMetric(JSONArray targets) {
        JSONObject settings = new JSONObject()
            .put("count_skipped_as_failure", false)
            .put("isolate_workspaces", true)
            .put("targets", targets);

        return new PortabilityPassRateMetric(defaultThresholds(), settings);
    }

    private ArrayList<Threshold> defaultThresholds() {
        JSONObject thresholds = new JSONObject()
            .put("critical", 0.50)
            .put("major", 0.25)
            .put("minor", 0.10);

        return ConfigLoader.resolveThresholds(thresholds);
    }

    private JSONObject localTarget(String name, String supportLevel, double weight, String... command) {
        return new JSONObject()
            .put("name", name)
            .put("mode", "local")
            .put("support_level", supportLevel)
            .put("weight", weight)
            .put("timeout_seconds", 20)
            .put("fail_severity", "major")
            .put("command", new JSONArray(command));
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private String testClassPath() {
        return System.getProperty("java.class.path");
    }
}
