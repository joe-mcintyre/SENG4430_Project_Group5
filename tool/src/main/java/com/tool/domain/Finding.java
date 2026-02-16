package com.tool.domain;

public record Finding(
        String tool,
        String ruleId,
        Severity severity,
        String message,
        String file,
        Integer line
) {}
