package com.vein.theme;

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
 * A theme/sector grouping (V8 {@code themes}). market CRYPTO | US | KR; unique
 * per (market, name).
 */
@Entity
@Table(name = "themes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Theme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String market;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private Theme(String market, String name, Instant collectedAt) {
        this.market = market;
        this.name = name;
        this.collectedAt = collectedAt;
    }

    public static Theme of(String market, String name, Instant collectedAt) {
        return new Theme(market, name, collectedAt);
    }
}
