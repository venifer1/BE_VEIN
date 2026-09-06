package com.vein.instrument;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.HangulChosung;

@Service
@Transactional(readOnly = true)
public class InstrumentService {

    /** API_CONTRACT §2: integrated search returns at most 30 rows. */
    private static final int MAX_RESULTS = 30;

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    /**
     * Unified 4-market instrument search. {@code market} null/blank searches all
     * markets. When {@code q} is a pure Korean 초성 query (e.g. "ㅅㅈ") it matches
     * each instrument's name by initial consonants; otherwise it's a plain
     * case-insensitive substring on symbol/name. Capped at {@value #MAX_RESULTS}.
     */
    public List<InstrumentDto> search(String q, String market, String status) {
        String effectiveMarket = (market == null || market.isBlank()) ? null : market;
        String effectiveStatus = (status == null || status.isBlank()) ? null : status;
        String effectiveQ = (q == null || q.isBlank()) ? null : q.trim();

        List<Instrument> matches;
        if (effectiveQ != null && HangulChosung.isChosungQuery(effectiveQ)) {
            matches = instrumentRepository.findForChosung(effectiveMarket, effectiveStatus).stream()
                    .filter(i -> HangulChosung.matches(i.getName(), effectiveQ))
                    .toList();
        } else {
            matches = instrumentRepository.search(effectiveQ, effectiveMarket, effectiveStatus);
        }
        return matches.stream()
                .limit(MAX_RESULTS)
                .map(InstrumentDto::from)
                .toList();
    }

    public Instrument getById(Long id) {
        return instrumentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Instrument not found: " + id));
    }
}
