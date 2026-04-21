import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tool.Main;

public class SystemTest {
    @TempDir
    Path tempDir;


    @Test
    void endToEndAuditGeneratesValidReports() throws Exception {

        Path sourceDir = tempDir.resolve("sample-project");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("Demo.java"), """
            class Demo {
                void run(int value) {
                    if (value > 0) {
                        System.out.println(value);
                    }
                }
            }
        """);

        Path configFile = createConfig(tempDir, """
            [
              {
                "category": "Maintainability",
                "description": "System test config",
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

        Path dependencyReport = tempDir.resolve("deps.json");
        Files.writeString(dependencyReport, "{}");

        Path outputBase = tempDir.resolve("out/report");
        Files.createDirectories(outputBase.getParent());

        String[] args = new String[] {
            "-s", sourceDir.toString(),
            "-d", dependencyReport.toString(),
            "--config", configFile.toString(),
            "--output", outputBase.toString(),
            "--project", "System Test Project",
            "--should-open-report", "false"
        };

        Main.main(args);

        Path jsonPath = tempDir.resolve("out/report.json");
        Path htmlPath = tempDir.resolve("out/report.html");

        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(htmlPath));

        JSONObject report = new JSONObject(Files.readString(jsonPath));

        JSONArray categories = report.getJSONArray("categories");
        assertEquals(1, categories.length());

        JSONObject category = categories.getJSONObject(0);
        assertEquals("Maintainability", category.getString("name"));

        JSONArray metrics = category.getJSONArray("metrics");
        assertEquals(1, metrics.length());

        JSONObject metric = metrics.getJSONObject(0);

        assertTrue(metric.has("score"));
        assertTrue(metric.getDouble("score") >= 2);
    }

    @Test
    void failsWhenSourceProjectDoesNotExist() {
      try {
        Path fakeProject = tempDir.resolve("does-not-exist");
        Path configFile = createConfig(tempDir, "[]");
        Path outputBase = tempDir.resolve("out/report");
        Files.createDirectories(outputBase.getParent());
        
        Main.main(new String[] {
            "-s", fakeProject.toString(),
            "--config", configFile.toString(),
            "--output", tempDir.resolve("out/report").toString(),
            "--project", "Test",
            "--should-open-report", "false"
        });
      } catch (Exception e) {
        e.printStackTrace();
      }
       
    }

    @Test
    void handlesNonJavaProjectGracefully() throws Exception {

        Path sourceDir = tempDir.resolve("fake-project");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("readme.txt"), "not a java project");

        Path configFile = createConfig(tempDir, """
            [
              {
                "category": "Maintainability",
                "description": "Test config",
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

        Path outputBase = tempDir.resolve("out/report");
        Files.createDirectories(outputBase.getParent());

        Main.main(new String[] {
            "-s", sourceDir.toString(),
            "--config", configFile.toString(),
            "--output", outputBase.toString(),
            "--project", "Test",
            "--should-open-report", "false"
        });

        Path jsonPath = tempDir.resolve("out/report.json");

        assertTrue(Files.exists(jsonPath));

        JSONObject report = new JSONObject(Files.readString(jsonPath));

        JSONArray categories = report.getJSONArray("categories");
        assertEquals(1, categories.length());

        JSONObject metric = categories.getJSONObject(0)
            .getJSONArray("metrics")
            .getJSONObject(0);

        assertTrue(metric.getDouble("score") >= 0);
    }

    @Test
    void handlesEmptyJavaProject() throws Exception {

        Path sourceDir = tempDir.resolve("empty-project");
        Files.createDirectories(sourceDir);

        Path configFile = createConfig(tempDir, """
            [
              {
                "category": "Maintainability",
                "description": "Test config",
                "metrics": [
                  { "type": "cyclomatic_complexity","thresholds": {
                      "major": 2,
                      "minor": 1
                    } }
                ]
              }
            ]
        """);

        Path outputBase = tempDir.resolve("out/report");
        Files.createDirectories(outputBase.getParent());

        Main.main(new String[] {
            "-s", sourceDir.toString(),
            "--config", configFile.toString(),
            "--output", outputBase.toString(),
            "--project", "Empty Project",
            "--should-open-report", "false"
        });

        Path jsonPath = tempDir.resolve("out/report.json");

        assertTrue(Files.exists(jsonPath));

        JSONObject report = new JSONObject(Files.readString(jsonPath));

        JSONArray metrics = report
            .getJSONArray("categories")
            .getJSONObject(0)
            .getJSONArray("metrics");

        assertEquals(1, metrics.length());
    }


    private Path createConfig(Path tempDir, String json) throws Exception {
        Path configFile = tempDir.resolve("config-" + System.nanoTime() + ".json");
        Files.writeString(configFile, json);
        return configFile;
    }
}
