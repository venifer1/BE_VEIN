package com.vein.market.index;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, Long> {

    Optional<MarketIndex> findTopByIndexKeyOrderByCollectedAtDesc(String indexKey);

    List<MarketIndex> findByIndexKeyOrderByCollectedAtDesc(String indexKey);
}
