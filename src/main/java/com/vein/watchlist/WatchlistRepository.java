package com.vein.watchlist;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    Optional<Watchlist> findByUserIdAndName(Long userId, String name);

    Optional<Watchlist> findFirstByUserIdOrderByIdAsc(Long userId);
}
