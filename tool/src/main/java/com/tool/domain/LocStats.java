package com.tool.domain;

public record LocStats(
        long javaFileCount,
        long logicalLoc
) {
    public double kloc() {
        return logicalLoc / 1000.0;
    }
}
