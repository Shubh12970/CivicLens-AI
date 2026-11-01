package com.civiclens.incident;

public enum IncidentCategory {
    ROAD_DAMAGE,
    STREETLIGHT,
    GARBAGE,
    WATER_LEAKAGE,
    DRAINAGE,
    FLOODING,
    SEWAGE,
    ELECTRICAL_HAZARD,
    TREE_FALL,
    TRAFFIC_SIGNAL,
    PUBLIC_PROPERTY_DAMAGE,
    OTHER;

    /** Categories that are closely related for duplicate matching (e.g. drainage vs flooding). */
    public boolean relatedTo(IncidentCategory other) {
        if (this == other) {
            return true;
        }
        return group() != null && group().equals(other.group());
    }

    private String group() {
        return switch (this) {
            case DRAINAGE, FLOODING, SEWAGE, WATER_LEAKAGE -> "WATER";
            case STREETLIGHT, ELECTRICAL_HAZARD, TRAFFIC_SIGNAL -> "ELECTRICAL";
            case ROAD_DAMAGE -> "ROAD";
            case TREE_FALL, PUBLIC_PROPERTY_DAMAGE -> "PUBLIC_SPACE";
            default -> null;
        };
    }

    public static IncidentCategory parseOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return IncidentCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
