// The kind of rundown for this one is it looks for a project root by looking for pom.xml. creates a reports folder if needed, if
// there already is a report it uses that, otherwise it runs OWASP Dependency Check through maven and if it works it returns the 
// Report path, if it fails it skips the security metric to stop breaking
package com.tool.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

//Util for locating or generating the OWSAP report
public final class DependencyCheckReportResolver {

    private DependencyCheckReportResolver() {
    }

    //Finds the project root by walking upward until pom.xml is found, creates reports in the file directory, if a supplied report is
    //there it will be used instead otherwise any old report is deleted and a maven gens a new one using OWASP, if successful new path
    //Is returned otherwise skipepd.
    public static Path ensureFreshReportExists(Path sourceRoot, Path suppliedReportPath) throws Exception {
        Path projectRoot = findProjectRoot(sourceRoot); //Find the project root
        Path reportsDir = projectRoot.resolve("reports");   //Store gen reports
        Path reportPath = reportsDir.resolve("dependency-check-report.json");   //Expected output path

        Files.createDirectories(reportsDir); //Create if no reports path exists

        // If the user explicitly provided a report, prefer that.
        if (suppliedReportPath != null && Files.exists(suppliedReportPath)) {
            return suppliedReportPath;
        }

        // Always regenerate a fresh report in reports/
        Files.deleteIfExists(reportPath);

        System.out.println("Generating fresh Dependency-Check report in: " + reportPath);


        //mvn.cmd - Windows
        //mvn.bat - Windows
        //mvn - macOS/Linux
        boolean generated = tryCommands(projectRoot, reportPath, List.of(
                List.of("mvn.cmd",
                        "org.owasp:dependency-check-maven:12.1.0:check", //Runs the OWASP plugin
                        "-Dformats=JSON",   //Generates the JSON so the metric can parse it
                        "-Dodc.outputDirectory=reports",    //Gen report goes into reports
                        "-DossindexAnalyzerEnabled=false",  //Disables or enables the OSS Index analyser
                        "-DfailOnError=false",  //Prevents Dependency check errors from failing the whole maven command
                        "-DautoUpdate=true"), //FALSE STOPS ALL UPDATES / TRUE ALLOWS IT TO UPDATE THE TOOL (This is for DEMO)
                List.of("mvn.bat",
                        "org.owasp:dependency-check-maven:12.1.0:check",
                        "-Dformats=JSON",
                        "-Dodc.outputDirectory=reports",
                        "-DossindexAnalyzerEnabled=false",
                        "-DfailOnError=false",
                        "-DautoUpdate=true"), //FALSE STOPS ALL UPDATES / TRUE ALLOWS IT TO UPDATE THE TOOL (This is for DEMO)
                List.of("mvn",
                        "org.owasp:dependency-check-maven:12.1.0:check",
                        "-Dformats=JSON",
                        "-Dodc.outputDirectory=reports",
                        "-DossindexAnalyzerEnabled=false",
                        "-DfailOnError=false",
                        "-DautoUpdate=true") //FALSE STOPS ALL UPDATES / TRUE ALLOWS IT TO UPDATE THE TOOL (This is for DEMO)
        ));

        if (generated && Files.exists(reportPath)) {
            return reportPath;
        }

        return null;
    }

    //Finds the root directory of the project
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

    //Tries each of the maven commands until one succesfully works
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
