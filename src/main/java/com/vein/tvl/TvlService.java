package com.vein.tvl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.tvl.provider.DefiLlamaProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * TVL data (API_CONTRACT §6): DefiLlama protocols/chains snapshots + protocol
 * history. Serves the latest persisted batch filtered by {@code q}/sorted by
 * {@code sort}; history is fetched live from DefiLlama (its own 30s TTL cache).
 */
@Service
@Slf4j
public class TvlService {

    public static final String PROTOCOL = "PROTOCOL";
    public static final String CHAIN = "CHAIN";

    private final TvlSnapshotRepository repository;
    private final DefiLlamaProvider provider;

    public TvlService(TvlSnapshotRepository repository, DefiLlamaProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    /** Latest batch for mode, filtered by q (name/category contains), sorted, ranked. */
    @Transactional(readOnly = true)
    public List<TvlDto.Row> list(String mode, String sort, String q) {
        String type = normalizeMode(mode);
        String query = q == null ? null : q.trim().toLowerCase();
        boolean byChange = sort != null && sort.trim().equalsIgnoreCase("CHANGE_7D");

        List<TvlSnapshot> batch = repository.findLatestBatch(type);
        List<TvlSnapshot> filtered = new ArrayList<>();
        for (TvlSnapshot t : batch) {
            if (query != null && !query.isEmpty()) {
                String name = t.getName() == null ? "" : t.getName().toLowerCase();
                String cat = t.getCategory() == null ? "" : t.getCategory().toLowerCase();
                if (!name.contains(query) && !cat.contains(query)) {
                    continue;
                }
            }
            filtered.add(t);
        }

        Comparator<TvlSnapshot> cmp = byChange
                ? Comparator.comparing(TvlSnapshot::getChange7d, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(TvlSnapshot::getTvl, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        filtered.sort(cmp);

        List<TvlDto.Row> rows = new ArrayList<>(filtered.size());
        int rank = 1;
        for (TvlSnapshot t : filtered) {
            rows.add(new TvlDto.Row(
                    t.getId(), rank++, t.getEntityType(), t.getName(), t.getCategory(), t.getChains(),
                    plain(t.getTvl()), plain(t.getMcap()), plain(t.getChange1d()), plain(t.getChange7d())));
        }
        return rows;
    }

    /** Protocol TVL history as a line series (CHAIN entities have no history). */
    @Transactional(readOnly = true)
    public TvlDto.History history(Long id) {
        TvlSnapshot snap = repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "TVL entity not found: " + id));
        if (!PROTOCOL.equals(snap.getEntityType())) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "TVL history is only available for protocols");
        }
        List<DefiLlamaProvider.HistoryPoint> points = provider.fetchHistory(snap.getExternalId());
        List<TvlDto.HistoryPoint> out = new ArrayList<>(points.size());
        for (DefiLlamaProvider.HistoryPoint p : points) {
            out.add(new TvlDto.HistoryPoint(TimeUtil.toIso(Instant.ofEpochSecond(p.date())), plain(p.tvl())));
        }
        return new TvlDto.History(snap.getId(), snap.getName(), out);
    }

    @Transactional(readOnly = true)
    public Instant lastCollectedAt() {
        return repository.findTopByOrderByCollectedAtDesc().map(TvlSnapshot::getCollectedAt).orElse(null);
    }

    /**
     * Refresh protocols + chains from DefiLlama and persist a new batch. Each leg
     * is fault-tolerant; old batches are pruned to keep the table bounded.
     */
    @Transactional
    public void refresh() {
        Instant now = Instant.now();
        int saved = 0;
        try {
            for (DefiLlamaProvider.Protocol p : provider.fetchProtocols()) {
                repository.save(TvlSnapshot.of(PROTOCOL, p.slug(), p.name(), p.category(),
                        String.join(",", p.chains()), p.tvl(), p.mcap(), p.change1d(), p.change7d(), now));
                saved++;
            }
        } catch (RuntimeException e) {
            log.warn("TVL protocols refresh failed: {}", e.getMessage());
        }
        try {
            for (DefiLlamaProvider.Chain c : provider.fetchChains()) {
                repository.save(TvlSnapshot.of(CHAIN, c.name(), c.name(), null, null,
                        c.tvl(), null, null, c.change7d(), now));
                saved++;
            }
        } catch (RuntimeException e) {
            log.warn("TVL chains refresh failed: {}", e.getMessage());
        }
        if (saved > 0) {
            // Keep only the batch just written (and anything newer); drop older snapshots.
            repository.deleteByCollectedAtBefore(now);
        }
        log.debug("TVL refresh saved {} rows", saved);
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return PROTOCOL;
        }
        String m = mode.trim().toUpperCase();
        if (!m.equals(PROTOCOL) && !m.equals(CHAIN)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "mode must be PROTOCOL or CHAIN");
        }
        return m;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
