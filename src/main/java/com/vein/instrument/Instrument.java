package com.vein.instrument;

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
 * Tradeable instrument (부록 D / 표 16). Maps the Flyway-owned
 * {@code instruments} table.
 */
@Entity
@Table(name = "instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String market;

    @Column(nullable = false, length = 16)
    private String exchange;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(length = 64)
    private String name;

    @Column(name = "quote_currency", nullable = false, length = 8)
    private String quoteCurrency;

    @Column(nullable = false, length = 16)
    private String status;

    /** Factory for newly-synced instruments (sidecar instrument-master sync). */
    public static Instrument of(String market, String exchange, String symbol,
                                String name, String quoteCurrency, String status) {
        Instrument i = new Instrument();
        i.market = market;
        i.exchange = exchange;
        i.symbol = symbol;
        i.name = name;
        i.quoteCurrency = quoteCurrency;
        i.status = status;
        return i;
    }

    /** Update the display name (instrument-master re-sync); no-op if blank/unchanged. */
    public void rename(String newName) {
        if (newName != null && !newName.isBlank() && !newName.equals(this.name)) {
            this.name = newName;
        }
    }
}
