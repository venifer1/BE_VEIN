package com.vein.watchlist;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An instrument pinned to a watchlist. Maps the Flyway-owned
 * {@code watchlist_items} table; PK is (watchlist_id, instrument_id).
 */
@Entity
@Table(name = "watchlist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchlistItem {

    @EmbeddedId
    private WatchlistItemId id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    private WatchlistItem(WatchlistItemId id) {
        this.id = id;
    }

    public static WatchlistItem of(Long watchlistId, Long instrumentId) {
        return new WatchlistItem(new WatchlistItemId(watchlistId, instrumentId));
    }
}
