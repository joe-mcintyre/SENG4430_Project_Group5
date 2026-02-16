package com.tool.app;

import java.nio.file.Path;

public record ProjectContext(
        String projectName,
        Path sourceRoot,
        Path spotbugsReport
) {}
