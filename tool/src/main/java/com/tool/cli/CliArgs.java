package com.tool.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.tool.util.ConfigLoader;

public final class CliArgs {
    private final String projectName;
    private final Path sourceRoot;
    private final Path configPath;
    private final Path outputPath;

    private CliArgs(String projectName, Path sourceRoot, Path configPath, Path outputPath) {
        this.projectName = projectName;
        this.sourceRoot = sourceRoot;
        this.configPath = configPath;
        this.outputPath = outputPath;
    }

    public static CliArgs parse(String[] args) {
        Map<String, String> values = parseArgs(args);

        if (values.containsKey("--help") || values.containsKey("-h")) {
            throw new IllegalArgumentException(usage());
        }

        String source = firstPresent(values, "--source", "-s");

        if (source == null)
            throw new IllegalArgumentException("--source is required.");

        Path sourcePath = Paths.get(source);
        Path configPath = ConfigLoader.resolveConfigPath(values.get("--config"));

        Path outputPath = Paths.get(
                values.getOrDefault("--output", "reports/reliability-report.json")
        );

        String defaultProject =
                sourcePath.getFileName() != null
                        ? sourcePath.getFileName().toString()
                        : "project";

        String projectName = values.getOrDefault("--project", defaultProject);

        return new CliArgs(projectName, sourcePath, configPath, outputPath);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new HashMap<>();

        if (args == null) return values;

        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("-")) continue;

            String key = args[i];
            String value = "true";

            if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                value = args[i + 1];
                i++;
            }

            values.put(key, value);
        }

        return values;
    }

    private static String firstPresent(Map<String, String> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k)) {
                return map.get(k);
            }
        }
        return null;
    }

    public String projectName() {
        return projectName;
    }

    public Path sourceRoot() {
        return sourceRoot;
    }

    public Path configPath() {
        return configPath;
    }

    public Path outputPath() {
        return outputPath;
    }

    public static String usage() {
        return """
            Usage:
              java -jar quality-auditor-tool.jar \\
                --project <name> \\
                --source <path-to-java-source-root> \\
                [--config <path-to-quality-auditor.config>] \\
                [--output <path-to-output-report.json>]

            Required:
              --source            Path to Java source directory (e.g. src/main/java)

            Optional:
              --project           Project label in report
              --config            Properties file with weights/thresholds
              --output            JSON report output location
            """;
    }
}
