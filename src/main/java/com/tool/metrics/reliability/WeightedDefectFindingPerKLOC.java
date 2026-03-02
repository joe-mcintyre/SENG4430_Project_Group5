package com.tool.metrics.reliability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ReturnStmt;

import com.tool.domain.Finding;
import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.metrics.Metric;
import com.tool.metrics.MetricResult;

/**
 * This metric uses heuristic source-based findings and computes:
 *
 * weightedPoints =
 *      20 * blockers
 *    + 10 * critical
 *    +  5 * major
 *    +  2 * minor
 *    +  0 * info
 *
 * effectiveKLOC = max(rawKLOC, minKlocForNormalization)
 * weightedDensityPerKloc = weightedPoints / effectiveKLOC
 *
 * Pass gates:
 * - density <= maxWeightedDensityPerKloc
 * - blockers <= maxBlockerFindings
 * - critical <= maxCriticalFindings
 *
 * - blocker findings are surfaced as CRITICAL findings in the report.
 */
public class WeightedDefectFindingPerKLOC extends Metric {
    private static final String NAME = "Reliability Findings Density";
    private static final String DESCRIPTION =
            "Measures reliability using source-level reliability findings density per KLOC.";

    private static final double BLOCKER_WEIGHT = 20.0;
    private static final double CRITICAL_WEIGHT = 10.0;
    private static final double MAJOR_WEIGHT = 5.0;
    private static final double MINOR_WEIGHT = 2.0;
    private static final double INFO_WEIGHT = 0.0;

    private static final double MAX_WEIGHTED_DENSITY_PER_KLOC = 10.0;
    private static final double MIN_KLOC_FOR_NORMALIZATION = 1.0;
    private static final int MAX_BLOCKER_FINDINGS = 0;
    private static final int MAX_CRITICAL_FINDINGS = 0;

    private int blockerCount;
    private int criticalCount;
    private int majorCount;
    private int minorCount;
    private int infoCount;

    private ArrayList<Finding> findings;

    public WeightedDefectFindingPerKLOC(ArrayList<Threshold> thresholds) {
        super(thresholds, NAME, DESCRIPTION);
    }

    @Override
    public MetricResult evaluate(Path projectPath) {
        if (projectPath == null) {
            throw new IllegalArgumentException("Project path cannot be null");
        }

        if (!Files.exists(projectPath)) {
            throw new IllegalArgumentException("Project path does not exist: " + projectPath);
        }

        ParserConfiguration cfg = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        JavaParser parser = new JavaParser(cfg);

        resetState();
        analyseProject(parser, projectPath);

        long rawLoc = countJavaLoc(projectPath);
        double rawKloc = rawLoc / 1000.0;
        double effectiveKloc = Math.max(rawKloc, MIN_KLOC_FOR_NORMALIZATION);

        double weightedPoints =
                (blockerCount * BLOCKER_WEIGHT)
                        + (criticalCount * CRITICAL_WEIGHT)
                        + (majorCount * MAJOR_WEIGHT)
                        + (minorCount * MINOR_WEIGHT)
                        + (infoCount * INFO_WEIGHT);

        double weightedDensityPerKloc = weightedPoints / effectiveKloc;

        double densityScore = scoreAgainstMaxValue(
                weightedDensityPerKloc,
                MAX_WEIGHTED_DENSITY_PER_KLOC
        );

        double blockerScore = scoreAgainstMaxCount(
                blockerCount,
                MAX_BLOCKER_FINDINGS
        );

        double criticalScore = scoreAgainstMaxCount(
                criticalCount,
                MAX_CRITICAL_FINDINGS
        );

        double finalScore = clamp01(
                Math.min(densityScore, Math.min(blockerScore, criticalScore))
        );

        findings.add(0, new Finding(
                Severity.INFO,
                String.format(
                        "Reliability summary: weightedPoints=%.2f, rawLOC=%d, rawKLOC=%.3f, effectiveKLOC=%.3f, densityPerKLOC=%.2f, blockers=%d, critical=%d, major=%d, minor=%d, info=%d",
                        weightedPoints,
                        rawLoc,
                        rawKloc,
                        effectiveKloc,
                        weightedDensityPerKloc,
                        blockerCount,
                        criticalCount,
                        majorCount,
                        minorCount,
                        infoCount
                ),
                projectPath.toString(),
                "project-wide",
                null
        ));

        if (weightedDensityPerKloc > MAX_WEIGHTED_DENSITY_PER_KLOC) {
            findings.add(new Finding(
                    Severity.MAJOR,
                    String.format(
                            "Weighted findings density exceeded maximum: %.2f > %.2f points/KLOC",
                            weightedDensityPerKloc,
                            MAX_WEIGHTED_DENSITY_PER_KLOC
                    ),
                    projectPath.toString(),
                    "project-wide",
                    null
            ));
        }

        if (blockerCount > MAX_BLOCKER_FINDINGS) {
            findings.add(new Finding(
                    Severity.CRITICAL,
                    String.format(
                            "Blocker findings exceeded maximum: %d > %d",
                            blockerCount,
                            MAX_BLOCKER_FINDINGS
                    ),
                    projectPath.toString(),
                    "project-wide",
                    null
            ));
        }

        if (criticalCount > MAX_CRITICAL_FINDINGS) {
            findings.add(new Finding(
                    Severity.CRITICAL,
                    String.format(
                            "Critical findings exceeded maximum: %d > %d",
                            criticalCount,
                            MAX_CRITICAL_FINDINGS
                    ),
                    projectPath.toString(),
                    "project-wide",
                    null
            ));
        }

        return new MetricResult(finalScore, findings, thresholds());
    }

    private void resetState() {
        blockerCount = 0;
        criticalCount = 0;
        majorCount = 0;
        minorCount = 0;
        infoCount = 0;
        findings = new ArrayList<>();
    }

    private void analyseProject(JavaParser parser, Path projectPath) {
        try (Stream<Path> paths = Files.walk(projectPath)) {
            for (Path path : paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList()) {

                ParseResult<CompilationUnit> result = parser.parse(path);

                if (result.getResult().isEmpty()) {
                    continue;
                }

                CompilationUnit cu = result.getResult().get();
                analyseCompilationUnit(cu, path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan project files: " + e.getMessage(), e);
        }
    }

    private void analyseCompilationUnit(CompilationUnit cu, Path path) {
        for (CatchClause catchClause : cu.findAll(CatchClause.class)) {
            inspectCatchClause(catchClause, path);
        }

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            inspectMethodCall(call, path);
        }

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            inspectMethodDeclaration(method, path);
        }

        for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
            inspectConstructorDeclaration(constructor, path);
        }
    }

    private void inspectCatchClause(CatchClause catchClause, Path path) {
        String caughtType = catchClause.getParameter().getType().asString();
        boolean broadCatch =
                caughtType.equals("Exception")
                        || caughtType.equals("Throwable")
                        || caughtType.equals("RuntimeException")
                        || caughtType.endsWith(".Exception")
                        || caughtType.endsWith(".Throwable")
                        || caughtType.endsWith(".RuntimeException");

        boolean emptyBody = catchClause.getBody().getStatements().isEmpty();

        if (emptyBody && broadCatch) {
            addFinding(
                    InternalSeverity.CRITICAL,
                    "Empty generic catch block catches " + caughtType,
                    path,
                    catchClause
            );
        } else if (emptyBody) {
            addFinding(
                    InternalSeverity.MAJOR,
                    "Empty catch block catches " + caughtType,
                    path,
                    catchClause
            );
        } else if (broadCatch) {
            addFinding(
                    InternalSeverity.MAJOR,
                    "Generic catch block catches " + caughtType,
                    path,
                    catchClause
            );
        }
    }

    private void inspectMethodCall(MethodCallExpr call, Path path) {
        String name = call.getNameAsString();

        if ("printStackTrace".equals(name)) {
            addFinding(
                    InternalSeverity.MAJOR,
                    "Use of printStackTrace() instead of structured logging/error handling",
                    path,
                    call
            );
        }

        if ("exit".equals(name)
                && call.getScope().isPresent()
                && "System".equals(call.getScope().get().toString())) {
            addFinding(
                    InternalSeverity.BLOCKER,
                    "Use of System.exit() can terminate the application abruptly",
                    path,
                    call
            );
        }

        if ("sleep".equals(name)
                && call.getScope().isPresent()
                && "Thread".equals(call.getScope().get().toString())) {
            addFinding(
                    InternalSeverity.MINOR,
                    "Use of Thread.sleep() may indicate fragile timing-based logic",
                    path,
                    call
            );
        }
    }

    private void inspectMethodDeclaration(MethodDeclaration method, Path path) {
        for (var thrownType : method.getThrownExceptions()) {
            String thrown = thrownType.asString();

            if (isGenericThrowable(thrown)) {
                addFinding(
                        InternalSeverity.MAJOR,
                        "Method declares broad thrown type: " + thrown,
                        path,
                        method
                );
            }
        }

        for (ReturnStmt returnStmt : method.findAll(ReturnStmt.class)) {
            if (returnStmt.getExpression().isPresent()
                    && returnStmt.getExpression().get().isNullLiteralExpr()) {
                addFinding(
                        InternalSeverity.MINOR,
                        "Method returns null, which may increase risk of null-related runtime failures",
                        path,
                        returnStmt
                );
            }
        }
    }

    private void inspectConstructorDeclaration(ConstructorDeclaration constructor, Path path) {
        for (var thrownType : constructor.getThrownExceptions()) {
            String thrown = thrownType.asString();

            if (isGenericThrowable(thrown)) {
                addFinding(
                        InternalSeverity.MAJOR,
                        "Constructor declares broad thrown type: " + thrown,
                        path,
                        constructor
                );
            }
        }
    }

    private boolean isGenericThrowable(String typeName) {
        return typeName.equals("Exception")
                || typeName.equals("Throwable")
                || typeName.equals("RuntimeException")
                || typeName.endsWith(".Exception")
                || typeName.endsWith(".Throwable")
                || typeName.endsWith(".RuntimeException");
    }

    private void addFinding(InternalSeverity severity, String message, Path path, Node node) {
        incrementSeverityCount(severity);

        findings.add(new Finding(
                toPublicSeverity(severity),
                message,
                path.toString(),
                resolveFunctionName(node),
                resolveLine(node)
        ));
    }

    private void incrementSeverityCount(InternalSeverity severity) {
        switch (severity) {
            case BLOCKER:
                blockerCount++;
                break;
            case CRITICAL:
                criticalCount++;
                break;
            case MAJOR:
                majorCount++;
                break;
            case MINOR:
                minorCount++;
                break;
            case INFO:
            default:
                infoCount++;
                break;
        }
    }

    private String resolveFunctionName(Node node) {
        if (node instanceof CallableDeclaration<?> callable) {
            return callable.getNameAsString();
        }

        return node.findAncestor(CallableDeclaration.class)
                .map(CallableDeclaration::getNameAsString)
                .orElse("unknown");
    }

    private Integer resolveLine(Node node) {
        return node.getBegin()
                .map(position -> position.line)
                .orElse(null);
    }

    /**
     * Since the public Severity enum has no BLOCKER value,
     * blocker findings are surfaced as CRITICAL.
     */
    private Severity toPublicSeverity(InternalSeverity severity) {
        switch (severity) {
            case BLOCKER:
            case CRITICAL:
                return Severity.CRITICAL;
            case MAJOR:
                return Severity.MAJOR;
            case MINOR:
                return Severity.MINOR;
            case INFO:
            default:
                return Severity.INFO;
        }
    }

    private long countJavaLoc(Path sourceRoot) {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root must be a directory: " + sourceRoot);
        }

        long totalLoc = 0;

        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path javaFile : javaFiles) {
                totalLoc += countLocInFile(javaFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to count Java LOC: " + e.getMessage(), e);
        }

        return totalLoc;
    }

    // This should only count non-empty, non-comment-only lines.
    private long countLocInFile(Path javaFile) throws IOException {
        List<String> lines = Files.readAllLines(javaFile);

        boolean inBlockComment = false;
        long loc = 0;

        for (String line : lines) {
            StringBuilder cleaned = new StringBuilder();
            int i = 0;

            while (i < line.length()) {
                if (inBlockComment) {
                    int end = line.indexOf("*/", i);
                    if (end == -1) {
                        i = line.length();
                    } else {
                        inBlockComment = false;
                        i = end + 2;
                    }
                    continue;
                }

                int blockStart = line.indexOf("/*", i);
                int lineComment = line.indexOf("//", i);

                if (blockStart == -1 && lineComment == -1) {
                    cleaned.append(line.substring(i));
                    break;
                }

                if (lineComment != -1 && (blockStart == -1 || lineComment < blockStart)) {
                    cleaned.append(line, i, lineComment);
                    break;
                }

                cleaned.append(line, i, blockStart);
                inBlockComment = true;
                i = blockStart + 2;
            }

            if (!cleaned.toString().trim().isEmpty()) {
                loc++;
            }
        }

        return loc;
    }

    private double scoreAgainstMaxValue(double actual, double allowedMax) {
        if (actual <= allowedMax) {
            return 1.0;
        }

        if (actual <= 0.0 || allowedMax <= 0.0) {
            return 0.0;
        }

        return allowedMax / actual;
    }

    private double scoreAgainstMaxCount(int actual, int allowedMax) {
        if (actual <= allowedMax) {
            return 1.0;
        }

        if (allowedMax == 0) {
            return 0.0;
        }

        return ((double) allowedMax) / ((double) actual);
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private enum InternalSeverity {
        BLOCKER,
        CRITICAL,
        MAJOR,
        MINOR,
        INFO
    }
}