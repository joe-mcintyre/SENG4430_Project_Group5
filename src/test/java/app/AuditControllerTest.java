package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tool.app.AuditController;
import com.tool.app.AuditResult;
import com.tool.domain.Category;
import com.tool.metrics.MetricResult;
import com.tool.util.ConfigLoader;

class AuditControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsAMissingProjectPath() {
        AuditController controller = new AuditController(ConfigLoader.resolveConfigPath("default_config.json"));
        Path missingPath = tempDir.resolve("missing-project");

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> controller.runAudit(missingPath)
        );

        assertTrue(error.getMessage().contains("does not exist"));
    }

    @Test
    void runsASimpleMaintainabilityAuditFromConfig() throws Exception {
        Path sourceRoot = tempDir.resolve("sample-src");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Demo.java"), """
            class Demo {
                void run(int value) {
                    if (value > 0) {
                        System.out.println(value);
                    }
                }
            }
            """);

        Path configPath = tempDir.resolve("test-config.json");
        Files.writeString(configPath, """
            [
              {
                "category": "Maintainability",
                "description": "Simple maintainability checks.",
                "metrics": [
                  {
                    "type": "cyclomatic_complexity",
                    "thresholds": {
                      "major": 2,
                      "minor": 1
                    }
                  }
                ]
              }
            ]
            """);

        AuditController controller = new AuditController(configPath);
        AuditResult result = controller.runAudit(sourceRoot);

        assertEquals(1, result.categories().size());

        Category category = result.categories().get(0);
        assertEquals("Maintainability", category.name());

        MetricResult metricResult = result.resultsFor(category).get(0);
        assertEquals("Cyclomatic Complexity", metricResult.metric().name());
        assertEquals(2.0, metricResult.score());
    }
}
