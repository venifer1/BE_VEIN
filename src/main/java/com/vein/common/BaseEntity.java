package com.vein.common;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

/**
 * Shared {@code created_at} for entities that carry it. Entities with an
 * IDENTITY id declare their own {@code @Id} field.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
