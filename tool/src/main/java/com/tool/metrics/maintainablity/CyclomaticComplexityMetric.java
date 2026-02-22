package com.tool.metrics.maintainablity;

import java.nio.file.Path;
import java.util.ArrayList;

import com.tool.domain.Finding;
import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

public class CyclomaticComplexityMetric extends Metric {
    public CyclomaticComplexityMetric(ArrayList<Threshold> thresholds) {
        super(thresholds, "Cyclomatic Complexity", "Measures the number of linearly independent paths through a program's source code. Higher values indicate more complex code that may be harder to maintain and understand.");  
    }
    
    @Override
    public MetricResult evaluate(Path projectPath) {
        ArrayList<Threshold> thresholds = thresholds();
        ArrayList<Finding> violations = new ArrayList<>();

        violations.add(new Finding(
            Severity.BLOCKER,
            "src/main/java/com/example/Calculator.java:25",
            "foo/app/bar.java",
            24
        ));

        return new MetricResult(0, violations, thresholds);
    }
  
}
