package com.vein.paper;

import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class PaperDto {

    private PaperDto() {
    }

    public record CreateAccountRequest(
            @DecimalMin(value = "0.00000001") String initialBalance,
            String baseCurrency) {
    }

    public record AccountResponse(String id, String baseCurrency, String initialBalance,
                                  String cashBalance, String status, int simulationRun) {
    }

    public record CreateOrderRequest(
            @NotNull Long instrumentId,
            Long signalId,
            @Pattern(regexp = "BUY|SELL") String side,
            @Pattern(regexp = "MARKET|LIMIT") String type,
            @Pattern(regexp = "SPOT|FUTURES") String investmentType,
            @Pattern(regexp = "LONG|SHORT") String positionSide,
            String price,
            @NotNull @DecimalMin(value = "0.00000001") String quantity,
            String leverage,
            Boolean reduceOnly,
            String timeframe) {
    }

    public record OrderResponse(String id, String accountId, Long instrumentId, String symbol,
                                String name, Long signalId,
                                String investmentType, String positionSide, String side, String type,
                                String price, String quantity, String leverage, boolean reduceOnly,
                                String status, FillResponse fill) {
    }

    public record FillResponse(String id, String price, String quantity, String fee,
                               String slippage, String liquiditySource, String filledAt) {
    }

    public record PortfolioResponse(AccountResponse account, String equity, String unrealizedPnl,
                                    String realizedPnl, List<PositionResponse> positions,
                                    List<OrderResponse> recentOrders) {
    }

    public record PositionResponse(Long instrumentId, String symbol, String name, String quantity,
                                   String investmentType, String positionSide, String avgPrice,
                                   String markPrice, String marketValue, String margin, String leverage,
                                   String unrealizedPnl, String realizedPnl) {
    }

    public record PerformanceResponse(String accountId, String totalReturnPct, String equity,
                                      String realizedPnl, String unrealizedPnl, int openPositions) {
    }
}
