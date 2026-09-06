package com.vein.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {
}
