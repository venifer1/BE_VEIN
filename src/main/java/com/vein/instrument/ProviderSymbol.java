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
 * Mapping of an instrument to a provider-specific symbol (부록 D).
 * Maps the Flyway-owned {@code provider_symbols} table.
 */
@Entity
@Table(name = "provider_symbols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(name = "provider_symbol", nullable = false, length = 32)
    private String providerSymbol;

    /** Factory for newly-synced provider-symbol mappings (sidecar instrument sync). */
    public static ProviderSymbol of(Long instrumentId, String provider, String providerSymbol) {
        ProviderSymbol p = new ProviderSymbol();
        p.instrumentId = instrumentId;
        p.provider = provider;
        p.providerSymbol = providerSymbol;
        return p;
    }
}
