package com.vein.funding;

/**
 * Funding-arb row (GET /funding-arb). Money/pct as String Decimal, time as ISO UTC.
 */
public record FundingDto(int rank, String symbol, String name, String fundingPct,
                         String upbitPrice, String bybitPrice, String nextFundingAt,
                         String expected1xPct, String expected2xPct) {
}
