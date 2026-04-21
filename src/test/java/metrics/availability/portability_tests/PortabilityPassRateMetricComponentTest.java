package metrics.availability.portability_tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import com.tool.metrics.MetricResult;
import com.tool.metrics.availability.PortabilityPassRateMetric;

import test.java.util.TestUtils;

// These sit a bit above unit tests and check how the moving pieces behave together.
public class PortabilityPassRateMetricComponentTest extends PortabilityMetricTestSupport {

    @Test
    void isolatesWorkspacePerTarget() throws Exception {
        // First target writes a file, second target makes sure it does not leak into its own run.
        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(probeTarget("write-marker", "supported", 0.0, "write", "marker.txt"))
            .put(probeTarget("assert-marker-missing", "supported", 1.0, "assert-missing", "marker.txt"))
        );

        MetricResult result = metric.evaluate(tempDir);

        assertEquals(0.0, result.score(), 0.0001);
        assertFalse(Files.exists(tempDir.resolve("marker.txt")));
    }

    @Test
    void resolvesExecutionRootFromNestedSourcePath() throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        TestUtils.writeMinimalPom(tempDir);

        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(probeTarget("check-pom", "supported", 1.0, "assert-exists", "pom.xml"))
        );

        MetricResult result = metric.evaluate(tempDir.resolve("src/main/java"));

        assertEquals(0.0, result.score(), 0.0001);
    }

    @Test
    void skipsExcludedPathsWhenCopyingAWorkspace() throws Exception {
        // .git should stay out of the copied workspace, otherwise the isolation logic is a bit pointless.
        Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve(".git/hidden.txt"), "do-not-copy");

        PortabilityPassRateMetric metric = newMetric(new JSONArray()
            .put(probeTarget("ignore-git-folder", "supported", 1.0, "assert-missing", ".git/hidden.txt"))
        );

        MetricResult result = metric.evaluate(tempDir);

        assertEquals(0.0, result.score(), 0.0001);
    }
}
