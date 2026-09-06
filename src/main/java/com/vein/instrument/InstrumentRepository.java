package com.vein.instrument;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    List<Instrument> findByMarketAndStatus(String market, String status);

    List<Instrument> findByMarket(String market);

    /** Match on the {@code (exchange, symbol)} unique key (instrument sync upsert). */
    Optional<Instrument> findByExchangeAndSymbol(String exchange, String symbol);

    /** Match a seeded/synced symbol within a market regardless of board (US: NASDAQ vs NYSE). */
    Optional<Instrument> findFirstByMarketAndSymbol(String market, String symbol);

    /**
     * Search by symbol or name containing {@code q} (case-insensitive), optionally
     * filtered by market and status. Null/blank {@code q}, null {@code market} and
     * null {@code status} each skip their filter (so {@code market=null} searches
     * all 4 markets).
     */
    @Query("""
            select i from Instrument i
            where (:market is null or i.market = :market)
              and (:status is null or i.status = :status)
              and (:q is null or :q = ''
                   or lower(i.symbol) like lower(concat('%', :q, '%'))
                   or lower(i.name) like lower(concat('%', :q, '%')))
            order by i.symbol asc
            """)
    List<Instrument> search(@Param("q") String q,
                            @Param("market") String market,
                            @Param("status") String status);

    /** All instruments in a market+status, for in-memory chosung filtering. */
    @Query("""
            select i from Instrument i
            where (:market is null or i.market = :market)
              and (:status is null or i.status = :status)
            order by i.symbol asc
            """)
    List<Instrument> findForChosung(@Param("market") String market,
                                    @Param("status") String status);
}
