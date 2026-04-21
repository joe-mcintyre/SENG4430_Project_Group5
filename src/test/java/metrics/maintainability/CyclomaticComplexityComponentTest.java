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
import com.tool.util.ResourceUtil;

import test.java.util.TestUtils;

public class CyclomaticComplexityComponentTest {
    ArrayList<Threshold> thresholds;

    @TempDir
    Path tempDir;

   @Test
    void evaluatesMetricThroughConfigPipeline() throws Exception {
        Path configPath = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\config1.json");

        ArrayList<Category> categories = ConfigLoader.loadCategories(configPath);

        Category maintainability = categories.get(0);
        Metric metric = maintainability.metrics().get(0);

        Path source = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\sample-project\\GUI-based-Algorithm-Calculator");
        MetricResult result = metric.evaluate(source);

        assertEquals(maintainability, metric.category());
        assertTrue(result.score() >= 0);
    }
}
