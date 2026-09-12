package com.vein.paper;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperAccountRepository extends JpaRepository<PaperAccount, Long> {

    Optional<PaperAccount> findTopByUserIdAndStatusOrderBySimulationRunDesc(Long userId, String status);

    List<PaperAccount> findByUserIdAndStatus(Long userId, String status);

    boolean existsByUserId(Long userId);
}
