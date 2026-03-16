package metrics.maintainability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.MetricResult;
import com.tool.metrics.maintainability.CyclomaticComplexityMetric;
import com.tool.util.ResourceUtil;

public class CyclomaticComplexityTest {
    ArrayList<Threshold> thresholds;

    // @BeforeEach
    // void setUp() {
    //     if(thresholds == null){
    //         thresholds = new ArrayList<>();
    //         thresholds.add(new Threshold(Severity.CRITICAL, 2));
    //         thresholds.add(new Threshold(Severity.CRITICAL, 2));
    //         thresholds.add(new Threshold(Severity.CRITICAL, 2));
    //         thresholds.add(new Threshold(Severity.CRITICAL, 2));
    //         thresholds.add(new Threshold(Severity.CRITICAL, 2));
    //     }
    // }

    // @AfterEach
    // void tearDown() {
    // }

    @Test
    void testEmptyBody(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Project1.java");
        MetricResult res = c.evaluate(p);
        assertEquals(1, res.score(), 0.00001, "Empty functions should be 1");
    }
}
