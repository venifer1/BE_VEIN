package com.vein.watchlist;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.instrument.InstrumentService;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;
import com.vein.signal.PatternSignal;
import com.vein.signal.PatternSignalRepository;
import com.vein.watchlist.WatchlistDto.InstrumentRef;
import com.vein.watchlist.WatchlistDto.RecentSignal;

/**
 * Manages each user's single "default" watchlist and its pinned instruments.
 */
@Service
@Transactional(readOnly = true)
public class WatchlistService {

    private static final String DEFAULT_NAME = "default";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository itemRepository;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentService instrumentService;
    private final CandleRepository candleRepository;
    private final PatternSignalRepository signalRepository;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            WatchlistItemRepository itemRepository,
                            InstrumentRepository instrumentRepository,
                            InstrumentService instrumentService,
                            CandleRepository candleRepository,
                            PatternSignalRepository signalRepository) {
        this.watchlistRepository = watchlistRepository;
        this.itemRepository = itemRepository;
        this.instrumentRepository = instrumentRepository;
        this.instrumentService = instrumentService;
        this.candleRepository = candleRepository;
        this.signalRepository = signalRepository;
    }

    @Transactional
    public Watchlist getOrCreateDefault(Long userId) {
        return watchlistRepository.findByUserIdAndName(userId, DEFAULT_NAME)
                .orElseGet(() -> {
                    try {
                        return watchlistRepository.save(Watchlist.of(userId, DEFAULT_NAME));
                    } catch (DataIntegrityViolationException race) {
                        // Concurrent create: re-read the row created by the other tx.
                        return watchlistRepository.findByUserIdAndName(userId, DEFAULT_NAME)
                                .orElseThrow(() -> race);
                    }
                });
    }

    public List<Long> instrumentIdsForUser(Long userId) {
        return watchlistRepository.findByUserIdAndName(userId, DEFAULT_NAME)
                .map(w -> itemRepository.findByIdWatchlistId(w.getId()).stream()
                        .map(it -> it.getId().getInstrumentId())
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public WatchlistDto getDefault(Long userId) {
        Watchlist wl = getOrCreateDefault(userId);
        List<WatchlistItem> items = itemRepository.findByIdWatchlistId(wl.getId());

        List<Long> instrumentIds = items.stream()
                .map(it -> it.getId().getInstrumentId())
                .toList();

        // Batch-load instruments once for the page to avoid N+1 lookups.
        Map<Long, Instrument> byId = instrumentRepository.findAllById(instrumentIds).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));

        List<WatchlistDto.Item> enriched = items.stream()
                .map(it -> toItem(it, byId.get(it.getId().getInstrumentId())))
                .filter(it -> it != null)
                .toList();

        return new WatchlistDto("wl_" + wl.getId(), wl.getName(), enriched);
    }

    /**
     * Maps a pinned item to its enriched DTO. Price/signal lookups are isolated
     * per item so a failure degrades to null fields rather than failing the whole
     * response.
     */
    private WatchlistDto.Item toItem(WatchlistItem item, Instrument instrument) {
        if (instrument == null) {
            return null;
        }
        Long instrumentId = instrument.getId();
        String createdAt = item.getCreatedAt() == null ? null : TimeUtil.toIso(item.getCreatedAt());

        String lastPrice = null;
        String lastPriceAt = null;
        try {
            Candle latest = candleRepository
                    .findTopByIdInstrumentIdOrderByIdOpenTimeDesc(instrumentId)
                    .orElse(null);
            if (latest != null) {
                lastPrice = latest.getClose() == null ? null : latest.getClose().toPlainString();
                lastPriceAt = latest.getId() == null ? null : TimeUtil.toIso(latest.getId().getOpenTime());
            }
        } catch (RuntimeException ignored) {
            // Resilient: leave price fields null on lookup failure.
        }

        RecentSignal recentSignal = null;
        try {
            recentSignal = signalRepository
                    .findTopByInstrumentIdOrderByDetectedAtDesc(instrumentId)
                    .map(WatchlistService::toRecentSignal)
                    .orElse(null);
        } catch (RuntimeException ignored) {
            // Resilient: leave recent_signal null on lookup failure.
        }

        return new WatchlistDto.Item(toRef(instrument), createdAt, lastPrice, lastPriceAt, recentSignal);
    }

    private static RecentSignal toRecentSignal(PatternSignal s) {
        return new RecentSignal(
                "sig_" + s.getId(),
                s.getType().name(),
                s.getStatus().name(),
                s.getTimeframe(),
                TimeUtil.toIso(s.getDetectedAt()),
                s.getSubtype());
    }

    @Transactional
    public InstrumentRef addItem(Long userId, Long instrumentId) {
        Instrument instrument = instrumentService.getById(instrumentId);
        if (!STATUS_ACTIVE.equalsIgnoreCase(instrument.getStatus())) {
            throw new ApiException(ErrorCode.INSTRUMENT_INACTIVE,
                    "Instrument inactive: " + instrumentId);
        }

        Watchlist wl = getOrCreateDefault(userId);
        WatchlistItemId id = new WatchlistItemId(wl.getId(), instrumentId);
        if (itemRepository.existsById(id)) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS,
                    "Instrument already in watchlist: " + instrumentId);
        }
        try {
            itemRepository.save(WatchlistItem.of(wl.getId(), instrumentId));
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(ErrorCode.ALREADY_EXISTS,
                    "Instrument already in watchlist: " + instrumentId);
        }
        return toRef(instrument);
    }

    @Transactional
    public void removeItem(Long userId, Long instrumentId) {
        Watchlist wl = getOrCreateDefault(userId);
        WatchlistItemId id = new WatchlistItemId(wl.getId(), instrumentId);
        if (!itemRepository.existsById(id)) {
            throw new ApiException(ErrorCode.ITEM_NOT_FOUND,
                    "Instrument not in watchlist: " + instrumentId);
        }
        itemRepository.deleteById(id);
    }

    private static InstrumentRef toRef(Instrument i) {
        return new InstrumentRef("ins_" + i.getId(), i.getSymbol(), i.getName());
    }
}
