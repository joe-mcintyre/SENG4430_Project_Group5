package com.tool.security;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

//Minimal manual CLI argument parser for the "security" command. (Extracts Project name, dependency check, thresholds config path and output path)
public final class SecurityCliArgs {
    private final String projectName;
    private final Path dependencyReportPath;
    private final Path thresholdsPath;
    private final Path outputPath;

    private SecurityCliArgs(String projectName,
                            Path dependencyReportPath,
                            Path thresholdsPath,
                            Path outputPath) {
        this.projectName = projectName;
        this.dependencyReportPath = dependencyReportPath;
        this.thresholdsPath = thresholdsPath;
        this.outputPath = outputPath;
    }

    public static SecurityCliArgs parse(String[] args) {
        if (args == null || args.length == 0 || contains(args, "--help") || contains(args, "-h")) {
            throw new IllegalArgumentException("Missing required arguments.");
        }

        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("-")) {
                continue;
            }

            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                value = args[i + 1];
                i++;
            }
            values.put(key, value);
        }

        String dep = firstPresent(values, "--dependency-report", "--depcheck-report", "--dependencycheck-report", "-d");
        if (dep == null) {
            throw new IllegalArgumentException("--dependency-report is required.");
        }

        Path depPath = Paths.get(dep);
        Path thresholdsPath = Paths.get(values.getOrDefault("--thresholds", "config/quality-auditor.properties"));
        Path outputPath = Paths.get(values.getOrDefault("--output", "reports/security-report.json"));

        String defaultProject = depPath.getFileName() != null ? depPath.getFileName().toString() : "project";
        String projectName = values.getOrDefault("--project", defaultProject);

        return new SecurityCliArgs(projectName, depPath, thresholdsPath, outputPath);
    }

    private static boolean contains(String[] args, String key) {
        for (String a : args) {
            if (key.equals(a)) return true;
        }
        return false;
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

    public Path dependencyReportPath() {
        return dependencyReportPath;
    }

    public Path thresholdsPath() {
        return thresholdsPath;
    }

    public Path outputPath() {
        return outputPath;
    }

    public static String usage() {
        return """
            Usage:
              java -jar quality-auditor-tool.jar security \\
                --project <name> \\
                --dependency-report <path-to-dependency-check-report.json> \\
                [--thresholds <path-to-quality-auditor.properties>] \\
                [--output <path-to-output-report.json>]

            Required:
              --dependency-report   OWASP Dependency-Check JSON report file

            Optional:
              --project             Project label in report
              --thresholds          Properties file with thresholds/weights
              --output              JSON report output location
            """;
    }
}
