package com.tool.security.model;

//Normalised vulnerability record.
public record SecurityFinding(
        String cveId,
        Double cvssScore,
        SecuritySeverity severity,
        String dependency,
        String description,
        Boolean patchAvailable
) {}
