package com.vein.signal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalEvidenceRepository extends JpaRepository<SignalEvidence, Long> {

    List<SignalEvidence> findBySignalIdOrderBySequenceNoAsc(Long signalId);
}
