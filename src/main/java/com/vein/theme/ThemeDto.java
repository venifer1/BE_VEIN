package com.vein.theme;

import java.util.List;

/** Theme DTOs (GET /themes, GET /themes/{id}/constituents). */
public final class ThemeDto {

    private ThemeDto() {
    }

    /** A theme summary row. */
    public record ThemeRow(Long id, String market, String name, int constituentCount,
                           int unclassifiedCount, int lowConfidenceCount) {
    }

    /** A constituent with its classification provenance. */
    public record Constituent(String instrumentRef, String displayName,
                              String classificationSource, String classificationConfidence) {
    }

    public record Constituents(Long themeId, String market, String name, List<Constituent> items) {
    }
}
