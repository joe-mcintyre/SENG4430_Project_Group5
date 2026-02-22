package com.tool;

import com.tool.app.AuditController;
import com.tool.app.AuditResult;
import com.tool.cli.CliArgs;
import com.tool.metrics.MetricResult;
import com.tool.reports.JsonReportWriter;

public class Main {
    public static void main(String[] args) {
        try {
            CliArgs cli = CliArgs.parse(args);

            AuditController controller = new AuditController(cli.configPath());

            AuditResult auditResult = controller.runAudit(cli.sourceRoot());

            // Temporary console output for results - can be removed or replaced with more detailed output in the future
            System.out.println("Audit completed successfully. Results:");
            auditResult.results().forEach(result -> System.out.println(result.toString()));

            JsonReportWriter writer = new JsonReportWriter(cli.outputPath());
            writer.appendMetadata("Tool version: 1.0.0, Audit timestamp: " + java.time.Instant.now().toString());
            for (MetricResult res : auditResult.results()) {
                writer.appendResult(res);
            }

            writer.writeReport();
            System.out.println("Report written to: " + cli.outputPath().toString());

        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println();
            System.err.println(CliArgs.usage());
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Audit failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
