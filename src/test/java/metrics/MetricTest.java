package metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

class MetricTest {

    @Test
    void rejectsNullThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new DummyMetric(null));
    }

    @Test
    void fallsBackToSingleArgumentEvaluateWhenNoOverrideExists() throws Exception {
        DummyMetric metric = new DummyMetric(new ArrayList<>());
        Path projectPath = Path.of("sample-project");
        Path dependencyReportPath = Path.of("reports", "dependency-check.json");

        MetricResult result = metric.evaluate(projectPath, dependencyReportPath);

        assertEquals(1, metric.singleArgumentCalls);
        assertSame(projectPath, metric.lastProjectPath);
        assertSame(metric, result.metric());
    }

    private static final class DummyMetric extends Metric {
        private int singleArgumentCalls;
        private Path lastProjectPath;

        private DummyMetric(ArrayList<Threshold> thresholds) {
            super(thresholds, "Dummy Metric", "Small helper metric for base class checks.");
        }

        @Override
        public MetricResult evaluate(Path projectPath) {
            singleArgumentCalls++;
            lastProjectPath = projectPath;
            return new MetricResult(this, 0.0, new ArrayList<>(), thresholds());
        }
    }
}
