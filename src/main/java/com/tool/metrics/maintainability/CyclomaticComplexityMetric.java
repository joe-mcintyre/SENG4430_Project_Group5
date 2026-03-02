package com.tool.metrics.maintainability;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import com.tool.domain.Finding;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

public class CyclomaticComplexityMetric extends Metric {
    private int methodCount;
    private int totalComplexity;
    private ArrayList<Finding> violations;


    public CyclomaticComplexityMetric(ArrayList<Threshold> thresholds) {
        super(
            thresholds,
            "Cyclomatic Complexity",
            "Measures the number of linearly independent paths through a program's source code."
        );
    }

    @Override
    public MetricResult evaluate(Path projectPath) {
        // Configure parser settings
        ParserConfiguration cfg = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        JavaParser parser = new JavaParser(cfg);

        // Reset State
        totalComplexity = 0;
        methodCount = 0;
        violations = new ArrayList<>();

        // Calculate project complexity
        calculateProjectComplexity(parser, projectPath);

        // Find average
        double averageComplexity = 0;
        if(methodCount > 0){
            averageComplexity = (double) totalComplexity / methodCount;
        }

        return new MetricResult(averageComplexity, violations, thresholds());
    }

    /**
     * Calculates the cyclomatic complexity of java project 
     * @param parser A preconfigured Java parser  
     * @param projectPath The root project path
     */
    private void calculateProjectComplexity(JavaParser parser, Path projectPath){
        try (Stream<Path> paths = Files.walk(projectPath)) {
            for (Path path : paths
                .filter(p -> p.toString().endsWith(".java"))
                .toList()) {

                ParseResult<CompilationUnit> result = parser.parse(path);

                if (result.getResult().isEmpty()) {
                    continue;
                }

                CompilationUnit cu = result.getResult().get();
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);

                calculateMethodComplexities(methods, path);        
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates the cyclomatic complexities of each method updating 
     * the total complexity, methodCount and adds any violations. 
     * @param methods The methods of a class which will be evaluated
     * @param path The path of the class
     */
    private void calculateMethodComplexities (List<MethodDeclaration> methods, Path path){
        for (MethodDeclaration method : methods) {
            int complexity = calculateMethodComplexity(method);

            totalComplexity += complexity;
            methodCount++;

            // Threshold method evaluation 
            for (Threshold threshold : thresholds()) {
                if (complexity > threshold.value()) {
                    String msg = "The method \"" + method.getNameAsString() + "\" has a cyclomatic complexity of " + complexity;

                    violations.add(new Finding(
                        threshold.severity(),
                        msg,
                        path + ":" +
                        method.getBegin().map(p -> p.line).orElse(-1),
                        method.getNameAsString(),
                        complexity
                    ));

                    // Report only the highest threshold.
                    break;  
                }
            }
        }
    }

    private int calculateMethodComplexity(MethodDeclaration method) {
        class ComplexityVisitor extends VoidVisitorAdapter<Void> {
            int complexity = 1;

            @Override
            public void visit(IfStmt n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(ForStmt n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(ForEachStmt n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(WhileStmt n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(DoStmt n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(SwitchEntry n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(CatchClause n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(ConditionalExpr n, Void arg) {
                complexity++;
                super.visit(n, arg);
            }

            @Override
            public void visit(BinaryExpr n, Void arg) {
                if (n.getOperator() == BinaryExpr.Operator.AND ||
                    n.getOperator() == BinaryExpr.Operator.OR) {
                    complexity++;
                }
                super.visit(n, arg);
            }
        }

        // Walk through the control flow adding any additional splits in the flow.
        ComplexityVisitor visitor = new ComplexityVisitor();
        visitor.visit(method, null);
        return visitor.complexity;
    }
}