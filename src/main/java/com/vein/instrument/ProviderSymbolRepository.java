package com.vein.instrument;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderSymbolRepository extends JpaRepository<ProviderSymbol, Long> {

    Optional<ProviderSymbol> findByProviderAndInstrumentId(String provider, Long instrumentId);

    List<ProviderSymbol> findByProvider(String provider);

    /** Guards the {@code (provider, provider_symbol)} unique key on sync upsert. */
    boolean existsByProviderAndProviderSymbol(String provider, String providerSymbol);
}
