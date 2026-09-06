package com.vein.alert;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vein.signal.SignalType;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByUserIdAndInstrumentIdAndSignalType(Long userId, Long instrumentId, SignalType signalType);

    List<Alert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Alert> findByEnabledTrue();

    List<Alert> findByInstrumentIdAndSignalTypeAndEnabledTrue(Long instrumentId, SignalType signalType);
}
