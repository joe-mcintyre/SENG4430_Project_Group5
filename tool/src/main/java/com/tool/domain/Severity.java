package com.tool.domain;

public enum Severity {
    BLOCKER(5),
    CRITICAL(4),
    MAJOR(3),
    MINOR(2),
    INFO(1);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }
}
