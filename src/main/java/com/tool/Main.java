package com.tool;

import com.tool.app.AuditController;
import com.tool.app.AuditResult;
import com.tool.cli.CliArgs;
import com.tool.reports.HTMLReportWriter;
import com.tool.reports.JSONReportWriter;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.tool.util.DependencyCheckReportResolver;

public class Main {
    public static void main(String[] args) {
        try {
            CliArgs cli = CliArgs.parse(args);

            AuditController controller = new AuditController(cli.configPath());
            Path dependencyReportPath = DependencyCheckReportResolver.ensureFreshReportExists(
                cli.sourceRoot(),
                cli.dependencyReportPath()
            );

            System.out.println("Running Audit");
            AuditResult auditResult = controller.runAudit(cli.sourceRoot(), dependencyReportPath);

            System.out.println("Audit complete generating reports...");
            Path basePath = cli.outputPath();

            Path jsonPath = Paths.get(basePath.toString() + ".json");
            JSONReportWriter jsonReport = new JSONReportWriter(jsonPath);
            jsonReport.writeReport(auditResult);

            Path htmlPath = Paths.get(basePath.toString() + ".html");
            HTMLReportWriter htmlReport = new HTMLReportWriter(htmlPath, cli.sourceRoot());
            htmlReport.writeReport(auditResult);

            System.out.println("Audit completed successfully.\nReport can be found: " + cli.outputPath().toAbsolutePath() + ".html");

            if(cli.shouldOpenReport()) {
                // Open the HTML report automatically
                try {
                    java.awt.Desktop.getDesktop().browse(htmlPath.toAbsolutePath().toUri());
                } catch (Exception e) {
                    System.err.println("Could not open the HTML report automatically: " + e.getMessage());
                }
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println();
            System.err.println(CliArgs.usage());
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Audit failed: " + ex.getMessage());
            System.exit(1);
        }
    }
}
