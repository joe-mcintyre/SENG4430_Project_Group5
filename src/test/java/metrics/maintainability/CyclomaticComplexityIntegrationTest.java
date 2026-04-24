package metrics.maintainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tool.domain.Category;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;
import com.tool.metrics.maintainability.CyclomaticComplexityMetric;
import com.tool.util.ConfigLoader;

import util.TestUtils;

public class CyclomaticComplexityIntegrationTest {
    ArrayList<Threshold> thresholds;

    @TempDir
    Path tempDir;

    // General cases
    @Test
    void loadsCyclomaticComplexityMetricFromConfigAndEvaluatesIt(){
        try {
            // Build a tiny config on the fly so we are testing the real wiring, not a mocked version of it.
            Files.createDirectories(tempDir.resolve("src/main/java"));
            TestUtils.writeMinimalPom(tempDir);

            JSONArray config = new JSONArray().put(new JSONObject()
                .put("category", "Maintainability")
                .put("description", "...")
                .put("metrics", new JSONArray().put(new JSONObject()
                    .put("type", "cyclomatic_complexity")
                    .put("thresholds", new JSONObject()
                        .put("critical", 10)
                        .put("major", 5)
                        .put("minor", 2))
            )));

            Path configPath = tempDir.resolve("maintainability-config.json");
            Files.writeString(configPath, config.toString(2));

            ArrayList<Category> categories = ConfigLoader.loadCategories(configPath);
            Metric metric = categories.get(0).metrics().get(0);
            MetricResult result = metric.evaluate(tempDir.resolve("src/main/java"));

            assertInstanceOf(CyclomaticComplexityMetric.class, metric);
            assertEquals(categories.get(0), metric.category());
            assertEquals(0.0, result.score(), 0.0001);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false, "Exception thrown during test: " + e.getMessage());
        }
    }
}
