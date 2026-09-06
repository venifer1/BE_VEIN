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
@Table(name = "paper_fills")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaperFill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal fee;

    @Column(nullable = false)
    private BigDecimal slippage;

    @Column(name = "liquidity_source", nullable = false, length = 24)
    private String liquiditySource;

    @Column(name = "filled_at", nullable = false)
    private Instant filledAt;

    public static PaperFill of(Long orderId, BigDecimal price, BigDecimal quantity, BigDecimal fee,
                               String liquiditySource) {
        PaperFill fill = new PaperFill();
        fill.orderId = orderId;
        fill.price = price;
        fill.quantity = quantity;
        fill.fee = fee;
        fill.slippage = BigDecimal.ZERO;
        fill.liquiditySource = liquiditySource;
        fill.filledAt = Instant.now();
        return fill;
    }
}
