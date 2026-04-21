package metrics.availability.portability_tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.io.TempDir;

import com.tool.domain.Threshold;
import com.tool.metrics.availability.PortabilityPassRateMetric;
import com.tool.util.ConfigLoader;

// This just keeps all the repeated portability test setup in one spot.
abstract class PortabilityMetricTestSupport {
    @TempDir
    protected Path tempDir;

    protected PortabilityPassRateMetric newMetric(JSONArray targets) {
        return newMetric(defaultSettings(targets));
    }

    protected PortabilityPassRateMetric newMetric(JSONObject settings) {
        return new PortabilityPassRateMetric(defaultThresholds(), settings);
    }

    protected JSONObject defaultSettings(JSONArray targets) {
        return new JSONObject()
            .put("count_skipped_as_failure", false)
            .put("isolate_workspaces", true)
            .put("workspace_excludes", new JSONArray().put(".git").put("target"))
            .put("targets", targets);
    }

    protected ArrayList<Threshold> defaultThresholds() {
        JSONObject thresholds = new JSONObject()
            .put("critical", 0.50)
            .put("major", 0.25)
            .put("minor", 0.10);

        return ConfigLoader.resolveThresholds(thresholds);
    }

    protected JSONObject localTarget(String name, String supportLevel, double weight, String... command) {
        return localTarget(name, supportLevel, weight, 20, command);
    }

    protected JSONObject localTarget(
        String name,
        String supportLevel,
        double weight,
        int timeoutSeconds,
        String... command
    ) {
        JSONArray commandArray = new JSONArray();
        for (String part : command) {
            commandArray.put(part);
        }

        return new JSONObject()
            .put("name", name)
            .put("mode", "local")
            .put("support_level", supportLevel)
            .put("weight", weight)
            .put("timeout_seconds", timeoutSeconds)
            .put("fail_severity", "major")
            .put("command", commandArray);
    }

    protected JSONObject probeTarget(String name, String supportLevel, double weight, String action, String... args) {
        return probeTarget(name, supportLevel, weight, 20, action, args);
    }

    protected JSONObject probeTarget(
        String name,
        String supportLevel,
        double weight,
        int timeoutSeconds,
        String action,
        String... args
    ) {
        ArrayList<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        command.add(testClassPath());
        // The probe is a tiny helper app so these tests can act a bit more like real target runs.
        command.add(PortabilityTargetProbe.class.getName());
        command.add(action);

        for (String arg : args) {
            command.add(arg);
        }

        return localTarget(name, supportLevel, weight, timeoutSeconds, command.toArray(String[]::new));
    }

    protected String javaExecutable() {
        String executable = System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    protected String differentOs() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return "linux";
        }
        if (osName.contains("linux")) {
            return "macos";
        }
        return "windows";
    }

    protected String testClassPath() {
        return System.getProperty("java.class.path");
    }
}
