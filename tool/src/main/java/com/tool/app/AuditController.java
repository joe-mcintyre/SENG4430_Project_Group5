package com.tool.app;

import com.tool.domain.Finding;
import com.tool.domain.LocStats;
import com.tool.domain.ReliabilityResult;
import com.tool.domain.ReliabilityThresholds;
import com.tool.metrics.ReliabilityMetricCalculator;
import com.tool.reports.JsonReportWriter;
import com.tool.scan.FindingProvider;
import com.tool.util.JavaLocCounter;

import java.nio.file.Path;
import java.util.List;

public class AuditController {

    private final FindingProvider findingProvider;
    private final JavaLocCounter locCounter;
    private final ReliabilityMetricCalculator calculator;
    private final JsonReportWriter reportWriter;

    public AuditController(FindingProvider findingProvider,
                           JavaLocCounter locCounter,
                           ReliabilityMetricCalculator calculator,
                           JsonReportWriter reportWriter) {
        this.findingProvider = findingProvider;
        this.locCounter = locCounter;
        this.calculator = calculator;
        this.reportWriter = reportWriter;
    }

    public ReliabilityResult run(ProjectContext project,
                                 ReliabilityThresholds thresholds,
                                 Path outputPath) throws Exception {
        LocStats locStats = locCounter.count(project.sourceRoot());
        List<Finding> findings = findingProvider.load(project.spotbugsReport());

        ReliabilityResult result = calculator.calculate(findings, locStats, thresholds);

        reportWriter.write(outputPath, project, locStats, thresholds, result);
        return result;
    }
}
