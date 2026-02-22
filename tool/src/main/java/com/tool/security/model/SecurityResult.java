package com.tool.security.model;

import java.util.EnumMap;
import java.util.List;

public record SecurityResult(
        int totalDependencies,
        int totalVulnerabilities,
        EnumMap<SecuritySeverity, Integer> countsBySeverity,
        int exploitableCount,
        int patchAvailableCount,
        int patchUnknownCount,
        int score,
        String classification,
        boolean passed,
        List<SecurityFinding> topFindings
) {
    public int count(SecuritySeverity s) {
        return countsBySeverity.getOrDefault(s, 0);
    }
}
