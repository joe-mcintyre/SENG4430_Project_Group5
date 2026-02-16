package com.tool.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class CliArgs {
    private final String projectName;
    private final Path sourceRoot;
    private final Path spotBugsReportPath;
    private final Path thresholdsPath;
    private final Path outputPath;

    private CliArgs(String projectName,
                    Path sourceRoot,
                    Path spotBugsReportPath,
                    Path thresholdsPath,
                    Path outputPath) {
        this.projectName = projectName;
        this.sourceRoot = sourceRoot;
        this.spotBugsReportPath = spotBugsReportPath;
        this.thresholdsPath = thresholdsPath;
        this.outputPath = outputPath;
    }

    public static CliArgs parse(String[] args) {
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

        String source = firstPresent(values, "--source", "-s");
        String spotbugs = firstPresent(values, "--spotbugs-report", "--spotbugs", "-r");

        if (source == null || spotbugs == null) {
            throw new IllegalArgumentException("Both --source and --spotbugs-report are required.");
        }

        Path sourcePath = Paths.get(source);
        Path reportPath = Paths.get(spotbugs);

        Path thresholdsPath = Paths.get(values.getOrDefault("--thresholds", "config/quality-auditor.properties"));
        Path outputPath = Paths.get(values.getOrDefault("--output", "reports/reliability-report.json"));

        String defaultProject = sourcePath.getFileName() != null ? sourcePath.getFileName().toString() : "project";
        String projectName = values.getOrDefault("--project", defaultProject);

        return new CliArgs(projectName, sourcePath, reportPath, thresholdsPath, outputPath);
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

    public Path sourceRoot() {
        return sourceRoot;
    }

    public Path spotBugsReportPath() {
        return spotBugsReportPath;
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
              java -jar quality-auditor-tool.jar \\
                --project <name> \\
                --source <path-to-java-source-root> \\
                --spotbugs-report <path-to-spotbugsXml.xml> \\
                [--thresholds <path-to-quality-auditor.properties>] \\
                [--output <path-to-output-report.json>]

            Required:
              --source            Path to Java source directory (e.g. src/main/java)
              --spotbugs-report   SpotBugs XML report file

            Optional:
              --project           Project label in report
              --thresholds        Properties file with weights/thresholds
              --output            JSON report output location
            """;
    }
}
