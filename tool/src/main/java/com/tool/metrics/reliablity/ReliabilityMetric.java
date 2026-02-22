// package com.tool.metrics.reliablity;

// import com.tool.domain.Finding;
// import com.tool.domain.LocStats;
// import com.tool.domain.ReliabilityResult;
// import com.tool.domain.ReliabilityThresholds;
// import com.tool.domain.Severity;

// import java.util.Comparator;
// import java.util.EnumMap;
// import java.util.List;

// public class ReliabilityMetric {

//     public ReliabilityResult calculate(List<Finding> findings,
//                                        LocStats locStats,
//                                        ReliabilityThresholds thresholds) {

//         EnumMap<Severity, Integer> counts = new EnumMap<>(Severity.class);
//         for (Severity s : Severity.values()) {
//             counts.put(s, 0);
//         }

//         for (Finding finding : findings) {
//             counts.merge(finding.severity(), 1, Integer::sum);
//         }

//         int weightedPoints = 0;
//         for (Severity severity : Severity.values()) {
//             weightedPoints += counts.get(severity) * thresholds.weightFor(severity);
//         }

//         // Normalization
//         double rawKloc = locStats.kloc();
//         double effectiveKloc = Math.max(rawKloc, thresholds.minKlocForNormalization());

//         // Weighted Reliability Findings Density
//         double density;
//         if (effectiveKloc <= 0) {
//             density = (weightedPoints == 0) ? 0.0 : Double.POSITIVE_INFINITY;
//         } else {
//             density = weightedPoints / effectiveKloc;
//         }

//         int blockerCount = counts.get(Severity.BLOCKER);
//         int criticalCount = counts.get(Severity.CRITICAL);

//         boolean pass = density <= thresholds.maxWeightedDensityPerKloc()
//                 && blockerCount <= thresholds.maxBlockerFindings()
//                 && criticalCount <= thresholds.maxCriticalFindings();

//         String grade = grade(density,
//                 thresholds.maxWeightedDensityPerKloc(),
//                 blockerCount,
//                 thresholds.maxBlockerFindings(),
//                 criticalCount,
//                 thresholds.maxCriticalFindings());

//         List<Finding> topFindings = findings.stream()
//                 .sorted(Comparator
//                         .comparingInt((Finding f) -> f.severity().rank()).reversed()
//                         .thenComparing(f -> f.file() == null ? "" : f.file())
//                         .thenComparing(f -> f.line() == null ? Integer.MAX_VALUE : f.line()))
//                 .limit(25)
//                 .toList();

//         return new ReliabilityResult(
//                 counts,
//                 findings.size(),
//                 weightedPoints,
//                 effectiveKloc,
//                 density,
//                 pass,
//                 grade,
//                 topFindings
//         );
//     }

//     private String grade(double density,
//                          double maxDensity,
//                          int blockers,
//                          int maxBlockers,
//                          int critical,
//                          int maxCritical) {

//         if (Double.isInfinite(density)) return "E";
//         if (blockers > maxBlockers) return "E";
//         if (critical > maxCritical) return "D";
//         if (maxDensity <= 0) return "E";

//         double ratio = density / maxDensity;
//         if (ratio <= 0.50) return "A";
//         if (ratio <= 0.80) return "B";
//         if (ratio <= 1.00) return "C";
//         if (ratio <= 1.20) return "D";
//         return "E";
//     }
// }
