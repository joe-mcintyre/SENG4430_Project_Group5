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

    public static Severity fromSpotBugs(Integer spotBugsRank, Integer spotBugsPriority) {
        if (spotBugsRank != null) {
            int r = spotBugsRank;
            if (r >= 1 && r <= 4) return BLOCKER;
            if (r <= 9) return CRITICAL;
            if (r <= 14) return MAJOR;
            if (r <= 20) return MINOR;
        }

        if (spotBugsPriority != null) {
            int p = spotBugsPriority;
            if (p == 1) return CRITICAL;
            if (p == 2) return MAJOR;
            if (p == 3) return MINOR;
        }

        return INFO;
    }
}
