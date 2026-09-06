package com.vein.paper;

import java.math.BigDecimal;
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

@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 24)
    private String type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "reference_type", length = 24)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "idempotency_key", nullable = false, length = 96)
    private String idempotencyKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public static LedgerEntry of(Long accountId, String type, BigDecimal amount, String currency,
                                 String referenceType, Long referenceId, String idempotencyKey) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = accountId;
        entry.type = type;
        entry.amount = amount;
        entry.currency = currency;
        entry.referenceType = referenceType;
        entry.referenceId = referenceId;
        entry.idempotencyKey = idempotencyKey;
        return entry;
    }
}
