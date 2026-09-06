package com.vein.watchlist;

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

/**
 * A user's watchlist. Maps the Flyway-owned {@code watchlists} table.
 * UNIQUE(user_id, name); the canonical list uses name {@code default}.
 */
@Entity
@Table(name = "watchlists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    private Watchlist(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public static Watchlist of(Long userId, String name) {
        return new Watchlist(userId, name);
    }
}
