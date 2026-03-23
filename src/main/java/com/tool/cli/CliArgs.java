package com.tool.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.tool.util.ConfigLoader;

public final class CliArgs {
    private final String projectName;
    private final Path sourceRoot;
    private final Path dependencyReportPath;
    private final Path configPath;
    private final Path outputPath;

    private CliArgs(String projectName,
                    Path sourceRoot,
                    Path dependencyReportPath,
                    Path configPath,
                    Path outputPath) {
        this.projectName = projectName;
        this.sourceRoot = sourceRoot;
        this.dependencyReportPath = dependencyReportPath;
        this.configPath = configPath;
        this.outputPath = outputPath;
    }

    public static CliArgs parse(String[] args) {
        Map<String, String> values = parseArgs(args);

        if (values.containsKey("--help") || values.containsKey("-h")) {
            throw new IllegalArgumentException(usage());
        }

        String source = firstPresent(values, "--source", "-s");
        if (source == null) {
            throw new IllegalArgumentException("--source is required.");
        }

        Path sourcePath = Paths.get(source);
        Path dependencyReportPath = resolveDependencyReportPath(
                sourcePath,
                firstPresent(values, "--dependency-report", "--depcheck-report", "--dependencycheck-report", "-d")
        );

        Path configPath = ConfigLoader.resolveConfigPath(values.get("--config"));
        Path outputPath = Paths.get(values.getOrDefault("--output", "reports/quality-report"));

        String defaultProject = sourcePath.getFileName() != null
                ? sourcePath.getFileName().toString()
                : "project";

        String projectName = values.getOrDefault("--project", defaultProject);

        return new CliArgs(projectName, sourcePath, dependencyReportPath, configPath, outputPath);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new HashMap<>();

        if (args == null) {
            return values;
        }

        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("-")) {
                continue;
            }

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

    private static Path resolveDependencyReportPath(Path sourcePath, String raw) {
        if (raw != null && !raw.isBlank()) {
            return Paths.get(raw);
        }
        return null;
    }

    public String projectName() {
        return projectName;
    }

    public Path sourceRoot() {
        return sourceRoot;
    }

    public Path dependencyReportPath() {
        return dependencyReportPath;
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
                [--dependency-report <path-to-dependency-check-report.json>] \\
                [--config <path-to-config.json>] \\
                [--output <path-to-output-report.html>]

            Required:
              --source            Path to Java source directory (e.g. src/main/java)

            Optional:
              --project           Project label in report
              --dependency-report OWASP Dependency-Check JSON report file
              --config            Path to JSON config file (defaults to built-in config if not provided)
              --output            Report output folder
            """;
    }
}