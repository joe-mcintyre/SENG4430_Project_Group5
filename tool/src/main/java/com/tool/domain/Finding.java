package com.tool.domain;

public record Finding(
        Severity severity,
        String message,
        String file,
        Integer line
) {}
