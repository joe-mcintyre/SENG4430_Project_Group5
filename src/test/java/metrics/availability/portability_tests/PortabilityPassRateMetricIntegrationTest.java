package metrics.availability.portability_tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.tool.domain.Category;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;
import com.tool.metrics.availability.PortabilityPassRateMetric;
import com.tool.util.ConfigLoader;

// These make sure the metric still plugs into the actual config loader flow properly.
public class PortabilityPassRateMetricIntegrationTest extends PortabilityMetricTestSupport {

    @Test
    void loadsPortabilityMetricFromConfigAndEvaluatesIt() throws Exception {
        // Build a tiny config on the fly so we are testing the real wiring, not a mocked version of it.
        Files.createDirectories(tempDir.resolve("src/main/java"));
        writeMinimalPom(tempDir);

        JSONArray config = new JSONArray().put(new JSONObject()
            .put("category", "Availability")
            .put("description", "Checks portability")
            .put("metrics", new JSONArray().put(new JSONObject()
                .put("type", "portability_pass_rate")
                .put("thresholds", new JSONObject()
                    .put("critical", 0.50)
                    .put("major", 0.25)
                    .put("minor", 0.10))
                .put("settings", defaultSettings(new JSONArray()
                    .put(probeTarget("check-pom", "supported", 1.0, "assert-exists", "pom.xml"))))))
        );

        Path configPath = tempDir.resolve("portability-config.json");
        Files.writeString(configPath, config.toString(2));

        ArrayList<Category> categories = ConfigLoader.loadCategories(configPath);
        Metric metric = categories.get(0).metrics().get(0);
        MetricResult result = metric.evaluate(tempDir.resolve("src/main/java"));

        assertInstanceOf(PortabilityPassRateMetric.class, metric);
        assertEquals(categories.get(0), metric.category());
        assertEquals(0.0, result.score(), 0.0001);
    }

    @Test
    void defaultConfigStillIncludesThePortabilityMetric() {
        // This is mainly here so the metric does not quietly fall out of the shipped config later on.
        ArrayList<Category> categories = ConfigLoader.loadCategories(ConfigLoader.resolveConfigPath(null));

        boolean foundPortabilityMetric = categories.stream()
            .flatMap(category -> category.metrics().stream())
            .anyMatch(metric -> metric instanceof PortabilityPassRateMetric);

        assertTrue(foundPortabilityMetric);
    }
}
