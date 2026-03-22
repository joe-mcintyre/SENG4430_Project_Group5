package com.tool.app;

import java.nio.file.Path;

/**
 * Shared runtime inputs for metrics.
 * Source-based metrics use sourceRoot while security metrics use dependencyReportPath.
 */
public record ProjectContext(
        Path sourceRoot,
        Path dependencyReportPath
) {}
