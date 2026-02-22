package com.tool.security.scan;

import com.tool.security.model.SecurityFinding;

import java.util.List;


//Normalised scan output.
public record SecurityScanData(
        int totalDependencies,
        List<SecurityFinding> findings
) {}
