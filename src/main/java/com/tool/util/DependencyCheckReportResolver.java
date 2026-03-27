package com.tool.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class DependencyCheckReportResolver {

    private static final DecimalFormat SECONDS_FORMAT = new DecimalFormat("0.00");
    private static final DecimalFormat MEGABYTES_FORMAT = new DecimalFormat("0.00");

    private DependencyCheckReportResolver() {
    }

    public static Path ensureFreshReportExists(Path sourceRoot,
                                               Path suppliedReportPath,
                                               Path suppliedDataDirectory,
                                               boolean noUpdate) throws Exception {
        Path projectRoot = findProjectRoot(sourceRoot);
        Path reportsDir = projectRoot.resolve("reports");
        Path reportPath = reportsDir.resolve("dependency-check-report.json");

        Files.createDirectories(reportsDir);

        if (suppliedReportPath != null && Files.exists(suppliedReportPath)) {
            return suppliedReportPath;
        }

        Files.deleteIfExists(reportPath);

        Path dataDirectory = suppliedDataDirectory != null
                ? resolveAgainstProjectRoot(projectRoot, suppliedDataDirectory)
                : null;

        if (dataDirectory != null) {
            Files.createDirectories(dataDirectory);
            System.out.println("Dependency-Check data directory: " + dataDirectory.toAbsolutePath());
        }

        System.out.println("Generating fresh Dependency-Check report in: " + reportPath);

        long start = System.nanoTime();

        boolean generated = tryCommands(projectRoot, reportPath, buildMavenCommands(dataDirectory, noUpdate));

        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
        System.out.println("Dependency-Check generation elapsed time: " + SECONDS_FORMAT.format(elapsedSeconds) + " seconds");

        if (dataDirectory != null && Files.exists(dataDirectory)) {
            long bytes = calculateDirectorySize(dataDirectory);
            System.out.println("Dependency-Check data directory size: "
                    + formatMegabytes(bytes) + " MB (" + bytes + " bytes)");
        }

        if (generated && Files.exists(reportPath)) {
            return reportPath;
        }

        return null;
    }

    private static List<List<String>> buildMavenCommands(Path dataDirectory, boolean noUpdate) {
        List<List<String>> commands = new ArrayList<>();
        commands.add(buildMavenCommand("mvn.cmd", dataDirectory, noUpdate));
        commands.add(buildMavenCommand("mvn.bat", dataDirectory, noUpdate));
        commands.add(buildMavenCommand("mvn", dataDirectory, noUpdate));
        return commands;
    }

    private static List<String> buildMavenCommand(String executable, Path dataDirectory, boolean noUpdate) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("org.owasp:dependency-check-maven:12.1.0:check");
        command.add("-Dformats=JSON");
        command.add("-Dodc.outputDirectory=reports");
        command.add("-DossindexAnalyzerEnabled=false");
        command.add("-DfailOnError=false");

        if (dataDirectory != null) {
            command.add("-DdataDirectory=" + dataDirectory.toAbsolutePath());
        }

        if (noUpdate) {
            command.add("-DautoUpdate=false");
        }

        return command;
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

    private static Path resolveAgainstProjectRoot(Path projectRoot, Path candidate) {
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        return projectRoot.resolve(candidate).normalize();
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

    private static long calculateDirectorySize(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            return 0L;
                        }
                    })
                    .sum();
        }
    }

    private static String formatMegabytes(long bytes) {
        double megabytes = bytes / 1024.0 / 1024.0;
        return MEGABYTES_FORMAT.format(megabytes);
    }
}
