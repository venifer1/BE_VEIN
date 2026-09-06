package com.vein.theme;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeRepository extends JpaRepository<Theme, Long> {

    List<Theme> findByMarket(String market);

    Optional<Theme> findByMarketAndName(String market, String name);

    long count();
}
