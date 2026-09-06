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
@Table(name = "paper_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaperAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "base_currency", nullable = false, length = 8)
    private String baseCurrency;

    @Column(name = "initial_balance", nullable = false)
    private BigDecimal initialBalance;

    @Column(name = "cash_balance", nullable = false)
    private BigDecimal cashBalance;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "simulation_run", nullable = false)
    private int simulationRun;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaperAccount create(Long userId, String baseCurrency, BigDecimal initialBalance,
                                      int simulationRun) {
        PaperAccount account = new PaperAccount();
        account.userId = userId;
        account.baseCurrency = baseCurrency;
        account.initialBalance = initialBalance;
        account.cashBalance = initialBalance;
        account.status = "ACTIVE";
        account.simulationRun = simulationRun;
        account.updatedAt = Instant.now();
        return account;
    }

    public void addCash(BigDecimal amount) {
        this.cashBalance = this.cashBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void subtractCash(BigDecimal amount) {
        this.cashBalance = this.cashBalance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = "ARCHIVED";
        this.updatedAt = Instant.now();
    }
}
