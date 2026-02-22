package com.tool.security;

import com.tool.security.calc.SecurityMetricCalculator;
import com.tool.security.model.SecurityResult;
import com.tool.security.thresholds.SecurityThresholdManager;
import com.tool.security.thresholds.SecurityThresholds;
import com.tool.security.scan.DependencyCheckJsonVulnerabilityProvider;
import com.tool.security.scan.DependencyVulnerabilityProvider;
import com.tool.security.report.SecurityJsonReportWriter;

import java.nio.file.Path;

//Security-only entry point, Orchestrates the entire security pipeline
public final class SecurityMain {

    public static void main(String[] args) {
        try {
            //Parse CLI arguments into a structured object.
            SecurityCliArgs cli = SecurityCliArgs.parse(args);
            //Load security thresholds (e.g., max critical allowed, minimum scores).
            SecurityThresholdManager thresholdManager = new SecurityThresholdManager();
            SecurityThresholds thresholds = thresholdManager.load(cli.thresholdsPath());
            //Load scan data (dependencies + vulnerabilities) from the Dependency-Check
            DependencyVulnerabilityProvider provider = new DependencyCheckJsonVulnerabilityProvider();
            var scanData = provider.load(cli.dependencyReportPath());
            //Convert raw scan findings into a computed SecurityResult:
            SecurityMetricCalculator calculator = new SecurityMetricCalculator();
            SecurityResult result = calculator.calculate(scanData, thresholds);
            //Write the full JSON report to disk.
            Path out = cli.outputPath();
            new SecurityJsonReportWriter().write(out, cli.projectName(), cli.dependencyReportPath(), thresholds, result);

            //Print a compact summary to stdout (human-readable).
            System.out.println();
            System.out.println("=== Security Audit Summary ===");
            System.out.println("Project: " + cli.projectName());
            System.out.println("Status : " + (result.passed() ? "PASS" : "FAIL"));
            System.out.println("Class  : " + result.classification());
            System.out.println("Score  : " + result.score() + " / 100");
            System.out.println("CVEs   : " + result.totalVulnerabilities());
            System.out.println("Report : " + out.toAbsolutePath());

            // CI-friendly exit codes
            if (!result.passed()) {
                System.exit("HIGH_RISK".equals(result.classification()) ? 2 : 1);
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println();
            System.err.println(SecurityCliArgs.usage());
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Security audit failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
