package com.tool.domain;

public record Finding(
        Severity severity,
        String message,
        String file,
        String function,
        Integer line
) {}
