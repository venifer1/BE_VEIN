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
@Table(name = "paper_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaperOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "investment_type", nullable = false, length = 12)
    private String investmentType;

    @Column(name = "position_side", length = 8)
    private String positionSide;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(nullable = false, length = 12)
    private String type;

    private BigDecimal price;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal leverage;

    @Column(name = "reduce_only", nullable = false)
    private boolean reduceOnly;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "fee_model_version", nullable = false, length = 32)
    private String feeModelVersion;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaperOrder create(Long accountId, Long instrumentId, Long signalId,
                                    String investmentType, String positionSide, String side,
                                    String type, BigDecimal price, BigDecimal quantity,
                                    BigDecimal leverage, boolean reduceOnly, String feeModelVersion) {
        PaperOrder order = new PaperOrder();
        order.accountId = accountId;
        order.instrumentId = instrumentId;
        order.signalId = signalId;
        order.investmentType = investmentType;
        order.positionSide = positionSide;
        order.side = side;
        order.type = type;
        order.price = price;
        order.quantity = quantity;
        order.leverage = leverage;
        order.reduceOnly = reduceOnly;
        order.status = "CREATED";
        order.feeModelVersion = feeModelVersion;
        order.updatedAt = Instant.now();
        return order;
    }

    public void fill(BigDecimal price) {
        this.price = price;
        this.status = "FILLED";
        this.updatedAt = Instant.now();
    }
}
