package com.vein.paper;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Table(name = "paper_positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaperPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "investment_type", nullable = false, length = 12)
    private String investmentType;

    @Column(name = "position_side", length = 8)
    private String positionSide;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "avg_price", nullable = false)
    private BigDecimal avgPrice;

    @Column(nullable = false)
    private BigDecimal margin;

    @Column(nullable = false)
    private BigDecimal leverage;

    @Column(name = "realized_pnl", nullable = false)
    private BigDecimal realizedPnl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaperPosition create(Long accountId, Long instrumentId, String investmentType,
                                       String positionSide, BigDecimal quantity, BigDecimal avgPrice,
                                       BigDecimal margin, BigDecimal leverage) {
        PaperPosition position = new PaperPosition();
        position.accountId = accountId;
        position.instrumentId = instrumentId;
        position.investmentType = investmentType;
        position.positionSide = positionSide;
        position.quantity = quantity;
        position.avgPrice = avgPrice;
        position.margin = margin;
        position.leverage = leverage;
        position.realizedPnl = BigDecimal.ZERO;
        position.updatedAt = Instant.now();
        return position;
    }

    public void buy(BigDecimal fillPrice, BigDecimal fillQuantity) {
        increase(fillPrice, fillQuantity, BigDecimal.ZERO, this.leverage);
    }

    public void increase(BigDecimal fillPrice, BigDecimal fillQuantity, BigDecimal addedMargin,
                         BigDecimal leverage) {
        BigDecimal currentValue = avgPrice.multiply(quantity);
        BigDecimal addedValue = fillPrice.multiply(fillQuantity);
        BigDecimal newQuantity = quantity.add(fillQuantity);
        this.avgPrice = currentValue.add(addedValue).divide(newQuantity, 8, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
        this.margin = this.margin.add(addedMargin);
        this.leverage = leverage;
        this.updatedAt = Instant.now();
    }

    public BigDecimal sell(BigDecimal fillPrice, BigDecimal fillQuantity, BigDecimal fee) {
        BigDecimal pnl = fillPrice.subtract(avgPrice).multiply(fillQuantity).subtract(fee);
        this.quantity = this.quantity.subtract(fillQuantity);
        this.realizedPnl = this.realizedPnl.add(pnl);
        this.updatedAt = Instant.now();
        return pnl;
    }

    public BigDecimal closeFutures(BigDecimal fillPrice, BigDecimal fillQuantity, BigDecimal fee) {
        BigDecimal direction = "SHORT".equals(positionSide) ? BigDecimal.valueOf(-1) : BigDecimal.ONE;
        BigDecimal pnl = fillPrice.subtract(avgPrice).multiply(fillQuantity).multiply(direction).subtract(fee);
        BigDecimal releasedMargin = margin.multiply(fillQuantity).divide(quantity, 8, RoundingMode.HALF_UP);
        this.quantity = this.quantity.subtract(fillQuantity);
        this.margin = this.margin.subtract(releasedMargin);
        this.realizedPnl = this.realizedPnl.add(pnl);
        this.updatedAt = Instant.now();
        return pnl.add(releasedMargin);
    }
}
