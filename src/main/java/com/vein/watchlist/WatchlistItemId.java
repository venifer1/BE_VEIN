package com.vein.watchlist;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite key for {@code watchlist_items} (watchlist_id, instrument_id).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchlistItemId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "watchlist_id", nullable = false)
    private Long watchlistId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    public WatchlistItemId(Long watchlistId, Long instrumentId) {
        this.watchlistId = watchlistId;
        this.instrumentId = instrumentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WatchlistItemId other)) {
            return false;
        }
        return Objects.equals(watchlistId, other.watchlistId)
                && Objects.equals(instrumentId, other.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(watchlistId, instrumentId);
    }
}
