package com.tool;

import com.tool.app.AuditController;
import com.tool.app.ProjectContext;
import com.tool.app.ThresholdManager;
import com.tool.cli.CliArgs;
import com.tool.domain.ReliabilityResult;
import com.tool.domain.ReliabilityThresholds;
import com.tool.metrics.ReliabilityMetricCalculator;
import com.tool.reports.JsonReportWriter;
import com.tool.scan.SpotBugsXmlFindingProvider;
import com.tool.util.JavaLocCounter;

public class Main {
    public static void main(String[] args) {
        try {
            CliArgs cli = CliArgs.parse(args);

            ThresholdManager thresholdManager = new ThresholdManager();
            ReliabilityThresholds thresholds = thresholdManager.load(cli.thresholdsPath());

            ProjectContext context = new ProjectContext(
                    cli.projectName(),
                    cli.sourceRoot(),
                    cli.spotBugsReportPath()
            );

            AuditController controller = new AuditController(
                    new SpotBugsXmlFindingProvider(),
                    new JavaLocCounter(),
                    new ReliabilityMetricCalculator(),
                    new JsonReportWriter()
            );

            ReliabilityResult result = controller.run(context, thresholds, cli.outputPath());

            System.out.println();
            System.out.println("=== Reliability Audit Summary ===");
            System.out.println("Project: " + context.projectName());
            System.out.println("Status : " + (result.passed() ? "PASS" : "FAIL"));
            System.out.println("Grade  : " + result.grade());
            System.out.printf("Density: %.3f (max %.3f)%n",
                    result.weightedDensityPerKloc(),
                    thresholds.maxWeightedDensityPerKloc());
            System.out.println("Report : " + cli.outputPath().toAbsolutePath());

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
