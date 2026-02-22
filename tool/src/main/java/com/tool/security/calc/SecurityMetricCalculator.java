package com.tool.security.calc;

import com.tool.security.model.SecurityFinding;
import com.tool.security.model.SecurityResult;
import com.tool.security.model.SecuritySeverity;
import com.tool.security.scan.SecurityScanData;
import com.tool.security.thresholds.SecurityThresholds;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;


//severity counts and the weighted security score:
//score = 100 - (6 * critical) - (3 * high) - (1 * medium)

public final class SecurityMetricCalculator {

    //Calculate the final security metric summary for a scan
    public SecurityResult calculate(SecurityScanData data, SecurityThresholds thresholds) {
        //Pull all findings
        List<SecurityFinding> findings = data.findings();

        //Store count for each severity (Every Severity = 0)
        EnumMap<SecuritySeverity, Integer> counts = new EnumMap<>(SecuritySeverity.class);
        for (SecuritySeverity s : SecuritySeverity.values()) {
            counts.put(s, 0);
        }

        //Patch tracking
        int patchAvailable = 0;
        int patchUnknown = 0;

        //Walk every finding and update
        for (SecurityFinding f : findings) {
            counts.merge(f.severity(), 1, Integer::sum);
            if (Boolean.TRUE.equals(f.patchAvailable())) patchAvailable++;
            else if (f.patchAvailable() == null) patchUnknown++;
        }

        //Relevnat severities for the scoring formula
        int critical = counts.getOrDefault(SecuritySeverity.CRITICAL, 0);
        int high = counts.getOrDefault(SecuritySeverity.HIGH, 0);
        int medium = counts.getOrDefault(SecuritySeverity.MEDIUM, 0);

        //Convert counts into a penalty
        int penalty = (6 * critical) + (3 * high) + (1 * medium);
        //Raw score is 100
        int score = clamp(100 - penalty, 0, 100);

        //defining exploitable
        int exploitable = critical + high;

        String classification;
        boolean passed;

        if (critical > thresholds.maxCriticalAllowed() || score < thresholds.minWarningScore()) { //Any critical overflow or very low score
            classification = "HIGH_RISK";
            passed = false;
        } else if (high > thresholds.maxHighAllowed() || score < thresholds.minAcceptableScore()) { //Too many highs or score below acceptable
            classification = "WARNING";
            passed = false;
        } else { //Acceptable and pass
            classification = "ACCEPTABLE";
            passed = true;
        }

        //Build top findings list for reporting, sorted by severity, CVSS score (Descending) and CVE id
        List<SecurityFinding> top = findings.stream()
                .sorted(Comparator
                        .comparingInt((SecurityFinding f) -> f.severity().weight()).reversed()
                        .thenComparing((SecurityFinding f) -> f.cvssScore() == null ? -1.0 : f.cvssScore(), Comparator.reverseOrder())
                        .thenComparing(SecurityFinding::cveId))
                .limit(25)
                .toList();

        //Return final result object
        return new SecurityResult(
                data.totalDependencies(),
                findings.size(),
                counts,
                exploitable,
                patchAvailable,
                patchUnknown,
                score,
                classification,
                passed,
                top
        );
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
