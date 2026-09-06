package com.vein.theme;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A theme constituent (V8 {@code theme_constituents}): a coin symbol / US ticker /
 * KR code classified into a theme, with its source + confidence.
 */
@Entity
@Table(name = "theme_constituents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThemeConstituent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    @Column(name = "instrument_ref", nullable = false, length = 64)
    private String instrumentRef;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "classification_source", nullable = false, length = 24)
    private String classificationSource;

    @Column(name = "classification_confidence", precision = 5, scale = 4)
    private BigDecimal classificationConfidence;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private ThemeConstituent(Long themeId, String instrumentRef, String displayName,
                             String classificationSource, BigDecimal classificationConfidence,
                             Instant collectedAt) {
        this.themeId = themeId;
        this.instrumentRef = instrumentRef;
        this.displayName = displayName;
        this.classificationSource = classificationSource;
        this.classificationConfidence = classificationConfidence;
        this.collectedAt = collectedAt;
    }

    public static ThemeConstituent of(Long themeId, String instrumentRef, String displayName,
                                      String classificationSource, BigDecimal classificationConfidence,
                                      Instant collectedAt) {
        return new ThemeConstituent(themeId, instrumentRef, displayName, classificationSource,
                classificationConfidence, collectedAt);
    }
}
