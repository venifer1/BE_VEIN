package com.vein.watchlist;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, WatchlistItemId> {

    List<WatchlistItem> findByIdWatchlistId(Long watchlistId);

    boolean existsById(WatchlistItemId id);

    void deleteById(WatchlistItemId id);
}
