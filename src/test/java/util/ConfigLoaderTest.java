package util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tool.domain.Category;
import com.tool.util.ConfigLoader;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void fallsBackToBundledConfigWhenNothingIsProvided() {
        Path configPath = ConfigLoader.resolveConfigPath(null);

        assertNotNull(configPath);
        assertTrue(Files.exists(configPath));
    }

    @Test
    void loadCategoriesAssignsEachMetricBackToItsCategory() {
        ArrayList<Category> categories = ConfigLoader.loadCategories(
            ConfigLoader.resolveConfigPath("default_config.json")
        );

        assertEquals(3, categories.size());
        for (Category category : categories) {
            assertFalse(category.metrics().isEmpty());
            for (var metric : category.metrics()) {
                assertSame(category, metric.category());
            }
        }
    }

    @Test
    void rejectsMixedThresholdOrdering() {
        JSONObject thresholds = new JSONObject()
            .put("critical", 10)
            .put("major", 4)
            .put("minor", 6);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> ConfigLoader.resolveThresholds(thresholds)
        );

        assertTrue(error.getMessage().contains("consistent order"));
    }

    @Test
    void wrapsBrokenJsonConfigAsALoadError() throws Exception {
        Path brokenConfig = tempDir.resolve("broken-config.json");
        Files.writeString(brokenConfig, "{ not-json }");

        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> ConfigLoader.loadCategories(brokenConfig)
        );

        assertTrue(error.getMessage().contains("Failed to load categories"));
    }
}
