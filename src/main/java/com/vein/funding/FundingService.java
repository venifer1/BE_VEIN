package com.vein.funding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.TimeUtil;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.provider.bybit.BybitProvider;
import com.vein.market.provider.upbit.UpbitMarketDataProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Funding arbitrage (API_CONTRACT §6 / funding_arb_tab.py): Bybit USDT-perp
 * funding matched with Upbit KRW spot on the common base coin. Expected return =
 * funding_pct × N − roundtrip fees, where roundtrip = 2×(Upbit 0.05% + Bybit
 * 0.055%) covering entry+exit on both legs. Serves the latest persisted batch.
 */
@Service
@Slf4j
public class FundingService {

    private static final BigDecimal UPBIT_SPOT_FEE_PCT = new BigDecimal("0.05");
    private static final BigDecimal BYBIT_PERP_FEE_PCT = new BigDecimal("0.055");
    /** Roundtrip = entry+exit on both legs = 2×(Upbit + Bybit). */
    private static final BigDecimal ROUNDTRIP_FEE_PCT =
            UPBIT_SPOT_FEE_PCT.add(BYBIT_PERP_FEE_PCT).multiply(BigDecimal.valueOf(2));

    private final FundingArbSnapshotRepository repository;
    private final InstrumentRepository instrumentRepository;
    private final UpbitMarketDataProvider upbit;
    private final BybitProvider bybit;

    public FundingService(FundingArbSnapshotRepository repository,
                          InstrumentRepository instrumentRepository,
                          UpbitMarketDataProvider upbit,
                          BybitProvider bybit) {
        this.repository = repository;
        this.instrumentRepository = instrumentRepository;
        this.upbit = upbit;
        this.bybit = bybit;
    }

    /** Latest batch, sorted by funding (desc default / funding_asc) and ranked. */
    @Transactional(readOnly = true)
    public List<FundingDto> list(String sort) {
        List<FundingArbSnapshot> batch = repository.findLatestBatch();
        boolean asc = sort != null && sort.trim().equalsIgnoreCase("funding_asc");
        Comparator<FundingArbSnapshot> cmp = Comparator.comparing(
                FundingArbSnapshot::getFundingPct, Comparator.nullsLast(Comparator.naturalOrder()));
        if (!asc) {
            cmp = cmp.reversed();
        }
        batch.sort(cmp);

        List<FundingDto> rows = new ArrayList<>(batch.size());
        int rank = 1;
        for (FundingArbSnapshot f : batch) {
            rows.add(new FundingDto(
                    rank++, f.getSymbol(), f.getName(), plain(f.getFundingPct()),
                    plain(f.getUpbitPrice()), plain(f.getBybitPrice()),
                    TimeUtil.toIso(f.getNextFundingAt()),
                    plain(f.getExpected1xPct()), plain(f.getExpected2xPct())));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Instant lastCollectedAt() {
        return repository.findTopByOrderByCollectedAtDesc()
                .map(FundingArbSnapshot::getCollectedAt).orElse(null);
    }

    /**
     * Refresh: fetch Bybit funding + Upbit KRW prices, match common base coins,
     * compute expected 1x/2x profit, persist a new batch. Fault-tolerant.
     */
    @Transactional
    public void refresh() {
        List<BybitProvider.Funding> fundings;
        try {
            fundings = bybit.fetchLinearFunding();
        } catch (RuntimeException e) {
            log.warn("funding refresh skipped — Bybit unavailable: {}", e.getMessage());
            return;
        }

        // Upbit KRW base -> (market, korean name). Common-coin join key is the base asset.
        List<Instrument> krw = instrumentRepository.findByMarketAndStatus("CRYPTO", "ACTIVE");
        Map<String, Instrument> upbitByBase = new HashMap<>();
        List<String> upbitMarkets = new ArrayList<>();
        for (Instrument i : krw) {
            String symbol = i.getSymbol();
            if (symbol == null || !symbol.startsWith("KRW-")) {
                continue;
            }
            upbitByBase.put(symbol.substring(4).toUpperCase(), i);
            upbitMarkets.add(symbol);
        }
        if (upbitByBase.isEmpty()) {
            return;
        }

        Map<String, BigDecimal> upbitPrices;
        try {
            upbitPrices = upbit.fetchTickerPrices(upbitMarkets);
        } catch (RuntimeException e) {
            log.warn("funding refresh skipped — Upbit prices unavailable: {}", e.getMessage());
            return;
        }

        Instant now = Instant.now();
        int saved = 0;
        for (BybitProvider.Funding f : fundings) {
            Instrument inst = upbitByBase.get(f.base());
            if (inst == null) {
                continue;
            }
            BigDecimal upbitPrice = upbitPrices.get(inst.getSymbol());
            if (upbitPrice == null) {
                continue;
            }
            BigDecimal exp1 = expectedProfitPct(f.fundingPct(), 1);
            BigDecimal exp2 = expectedProfitPct(f.fundingPct(), 2);
            repository.save(FundingArbSnapshot.of(
                    f.base(), inst.getName(), f.fundingPct(), upbitPrice, f.lastPrice(),
                    f.nextFundingAt(), exp1, exp2, now));
            saved++;
        }
        if (saved > 0) {
            repository.deleteByCollectedAtBefore(now);
        }
        log.debug("funding refresh saved {} rows", saved);
    }

    /** funding_pct × count − roundtrip fees (funding_arb_tab._calc_expected_profit_pct). */
    static BigDecimal expectedProfitPct(BigDecimal fundingPct, int count) {
        if (fundingPct == null) {
            return null;
        }
        return fundingPct.multiply(BigDecimal.valueOf(Math.max(0, count)))
                .subtract(ROUNDTRIP_FEE_PCT)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
