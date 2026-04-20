package com.tool.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DependencyCheckReportResolver {

    private DependencyCheckReportResolver() {
    }

    public static Path ensureFreshReportExists(Path sourceRoot, Path suppliedReportPath) throws Exception {
        Path projectRoot = findProjectRoot(sourceRoot);
        Path reportsDir = projectRoot.resolve("reports");
        Path reportPath = reportsDir.resolve("dependency-check-report.json");

        Files.createDirectories(reportsDir);

        // If the user explicitly provided a report, prefer that.
        if (suppliedReportPath != null && Files.exists(suppliedReportPath)) {
            return suppliedReportPath;
        }

        // Always regenerate a fresh report in reports/
        Files.deleteIfExists(reportPath);

        System.out.println("Generating fresh Dependency-Check report in: " + reportPath);

        boolean generated = tryCommands(projectRoot, reportPath, List.of(
                List.of("mvn.cmd",
                        "org.owasp:dependency-check-maven:12.1.0:check",
                        "-Dformats=JSON",
                        "-Dodc.outputDirectory=reports",
                        "-DossindexAnalyzerEnabled=false",
                        "-DfailOnError=false",
                        "-DautoUpdate=false"),
                List.of("mvn.bat",
                        "org.owasp:dependency-check-maven:12.1.0:check",
                        "-Dformats=JSON",
                        "-Dodc.outputDirectory=reports",
                        "-DossindexAnalyzerEnabled=false",
                        "-DfailOnError=false",
                        "-DautoUpdate=false"),
                List.of("mvn",
                        "org.owasp:dependency-check-maven:12.1.0:check",
                        "-Dformats=JSON",
                        "-Dodc.outputDirectory=reports",
                        "-DossindexAnalyzerEnabled=false",
                        "-DfailOnError=false",
                        "-DautoUpdate=false")
        ));

        if (generated && Files.exists(reportPath)) {
            return reportPath;
        }

        return null;
    }

    private static Path findProjectRoot(Path sourceRoot) {
        if (sourceRoot == null) {
            return Path.of(".").toAbsolutePath().normalize();
        }

        Path current = sourceRoot.toAbsolutePath().normalize();

        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }

        return sourceRoot.toAbsolutePath().normalize();
    }

    private static boolean tryCommands(Path workingDirectory, Path expectedReport, List<List<String>> commands)
            throws InterruptedException {
        for (List<String> command : commands) {
            try {
                Process process = new ProcessBuilder(command)
                        .directory(workingDirectory.toFile())
                        .inheritIO()
                        .start();

                int exitCode = process.waitFor();

                if (Files.exists(expectedReport)) {
                    return true;
                }

                if (exitCode == 0) {
                    return true;
                }
            } catch (IOException ignored) {
                // Try next command
            }
        }
        return false;
    }
}
