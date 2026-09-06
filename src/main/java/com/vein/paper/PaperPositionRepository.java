package com.vein.paper;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {

    List<PaperPosition> findByAccountId(Long accountId);

    Optional<PaperPosition> findByAccountIdAndInstrumentId(Long accountId, Long instrumentId);

    Optional<PaperPosition> findByAccountIdAndInstrumentIdAndInvestmentTypeAndPositionSide(
            Long accountId, Long instrumentId, String investmentType, String positionSide);
}
