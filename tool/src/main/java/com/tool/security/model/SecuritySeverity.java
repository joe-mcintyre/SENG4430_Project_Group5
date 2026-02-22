package com.tool.security.model;


//Severity levels used for vulnerability scoring.
public enum SecuritySeverity {
    CRITICAL(6),
    HIGH(3),
    MEDIUM(1),
    LOW(0),
    UNKNOWN(0);

    private final int weight;

    SecuritySeverity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public static SecuritySeverity fromString(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "CRITICAL" -> CRITICAL;
            case "HIGH" -> HIGH;
            case "MEDIUM", "MODERATE" -> MEDIUM;
            case "LOW" -> LOW;
            default -> UNKNOWN;
        };
    }

    public static SecuritySeverity fromCvss(Double score) {
        if (score == null) return UNKNOWN;
        if (score >= 9.0) return CRITICAL;
        if (score >= 7.0) return HIGH;
        if (score >= 4.0) return MEDIUM;
        return LOW;
    }
}
