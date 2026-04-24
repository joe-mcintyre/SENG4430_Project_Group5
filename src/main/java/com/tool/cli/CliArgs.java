package com.tool.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.tool.util.ConfigLoader;

public class CliArgs {
    private final String projectName;
    private final Path sourceRoot;
    private final Path dependencyReportPath; //Path to the dep report. Security metric needs this to run, if null security is skipped
    private final Path configPath;
    private final Path outputPath;
    private final boolean shouldOpenReport;

    private CliArgs(String projectName,
                    Path sourceRoot,
                    Path dependencyReportPath,
                    Path configPath,
                    Path outputPath,
                    boolean shouldOpenReport) {
        this.projectName = projectName;
        this.sourceRoot = sourceRoot;
        this.dependencyReportPath = dependencyReportPath;   //Store this to be used later for security metric
        this.configPath = configPath;
        this.outputPath = outputPath;
        this.shouldOpenReport = shouldOpenReport;
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
        Path dependencyReportPath = resolveDependencyReportPath( //Looking for OWASP Dependency report path. Multiple names supported. Helps for testing
                sourcePath,
                firstPresent(values, "--dependency-report", "--depcheck-report", "--dependencycheck-report", "-d")
        );

        Path configPath = ConfigLoader.resolveConfigPath(values.get("--config"));
        Path outputPath = Paths.get(values.getOrDefault("--output", "reports/quality-report"));

        String defaultProject = sourcePath.getFileName() != null
                ? sourcePath.getFileName().toString()
                : "project";

        String projectName = values.getOrDefault("--project", defaultProject);

        boolean shouldOpenReport = Boolean.parseBoolean(values.getOrDefault("--should-open-report", "true"));

        return new CliArgs(projectName, sourcePath, dependencyReportPath, configPath, outputPath, shouldOpenReport);
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

    //If user provides a path, its turned into object, otherwise it returns null
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

    //Controller/audit pipeline have access to optional report path
    public Path dependencyReportPath() {
        return dependencyReportPath;
    }

    public Path configPath() {
        return configPath;
    }

    public Path outputPath() {
        return outputPath;
    }

    public boolean shouldOpenReport() {
        return shouldOpenReport;
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