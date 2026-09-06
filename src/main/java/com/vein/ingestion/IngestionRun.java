package com.vein.ingestion;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit record of an ingestion job (부록 D / 표 16). Maps the Flyway-owned
 * {@code ingestion_runs} table.
 */
@Entity
@Table(name = "ingestion_runs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(name = "job_type", nullable = false, length = 24)
    private String jobType;

    @Column(length = 4)
    private String timeframe;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "fetched_count")
    private Integer fetchedCount = 0;

    @Column(name = "inserted_count")
    private Integer insertedCount = 0;

    @Column(name = "gap_count")
    private Integer gapCount = 0;

    @Column(name = "error_code", length = 48)
    private String errorCode;

    public static IngestionRun start(String provider, String jobType, String timeframe, Instant now) {
        IngestionRun run = new IngestionRun();
        run.provider = provider;
        run.jobType = jobType;
        run.timeframe = timeframe;
        run.startedAt = now;
        run.status = "RUNNING";
        run.fetchedCount = 0;
        run.insertedCount = 0;
        run.gapCount = 0;
        return run;
    }
}
