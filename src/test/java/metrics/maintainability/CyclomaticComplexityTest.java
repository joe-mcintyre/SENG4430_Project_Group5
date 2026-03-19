package metrics.maintainability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.MetricResult;
import com.tool.metrics.maintainability.CyclomaticComplexityMetric;
import com.tool.util.ResourceUtil;

public class CyclomaticComplexityTest {
    ArrayList<Threshold> thresholds;

    // General cases
    @Test
    void testEmpty(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Empty.java");
        MetricResult res = c.evaluate(p);
        assertEquals(1, res.score(), 0.00001, "Empty functions should be 1");
    }

    @Test
    void testEmptyAverage(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\EmptyAverage.java");
        MetricResult res = c.evaluate(p);
        assertEquals(1, res.score(), 0.00001, "Empty functions should be 1");
    }
    
    @Test
    void testAverage(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Average1.java");
        MetricResult res = c.evaluate(p);
        assertEquals((double)(2 + 1 + 1) / 3, res.score(), 0.1, "1 conditional and 2 trivial functions should be 1.33");
    }

    // Conditionals and decisions
    @Test
    void testDecision1(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Decision1.java");
        MetricResult res = c.evaluate(p);
        assertEquals(4, res.score(), 0.1, "Deeply nested decisions");
    }

    @Test
    void testDecision2(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Decision2.java");
        MetricResult res = c.evaluate(p);
        assertEquals(4, res.score(), 0.1, "Deeply nested decisions");
    }
    
    @Test
    void testConditional1(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Conditional1.java");
        MetricResult res = c.evaluate(p);
        assertEquals(5, res.score(), 0.1, "Nested conditionals");
    }

    @Test
    void testConditional2(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Conditional2.java");
        MetricResult res = c.evaluate(p);
        assertEquals(5, res.score(), 0.1, "Nested conditionals");
    }

    @Test
    void testConditional3(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Conditional3.java");
        MetricResult res = c.evaluate(p);
        assertEquals(3, res.score(), 0.1, "Nested conditionals");
    }

    @Test
    void testSwitch1(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Switch1.java");
        MetricResult res = c.evaluate(p);
        assertEquals(7, res.score(), 0.1, "Nested conditionals");
    }

    @Test
    void testSwitch2(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Switch2.java");
        MetricResult res = c.evaluate(p);
        assertEquals(9, res.score(), 0.1, "Nested conditionals");
    }
    
    @Test
    void testTryCatch1(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\TryCatch1.java");
        MetricResult res = c.evaluate(p);
        assertEquals(2, res.score(), 0.1, "Nested conditionals");
    }
    
    @Test
    void testTryCatch2(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\TryCatch2.java");
        MetricResult res = c.evaluate(p);
        assertEquals(4, res.score(), 0.1, "Nested conditionals");
    }

    @Test
    void testComplicated1(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Complex1.java");
        MetricResult res = c.evaluate(p);
        assertEquals(5, res.score(), 0.1, "Nested conditionals");
    }

    @Test
    void testComplicated2(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\Complex2.java");
        MetricResult res = c.evaluate(p);
        assertEquals((double)(5 + 6) / 2, res.score(), 0.1, "Nested conditionals");
    }
    // Edge cases 
    @Test
    void testNoneValidJavaFile(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\NotValidJava.java");
        MetricResult res = c.evaluate(p);
        assertEquals(0, res.score(), 0.1, "Failed java files should not be checked");
    }

    @Test
    void testNoneJavaFile(){
        ArrayList<Threshold> thresholds = new ArrayList<>();
        CyclomaticComplexityMetric c = new CyclomaticComplexityMetric(thresholds);
        Path p = ResourceUtil.getResourcePath("metrics\\maintainability\\cyclomatic_complexity\\NotJava.c");
        MetricResult res = c.evaluate(p);
        assertEquals(0, res.score(), 0.1, "None java files should not be checked");
    }
}
