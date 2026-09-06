package com.vein.supply;

/** Supply list row (GET /supply). supply_tab.py columns; money/supply as String Decimal. */
public record SupplyDto(Integer rank, String coingeckoId, String name, String symbol,
                        String priceUsd, String marketCap, String circulating, String totalSupply,
                        String maxSupply, String circulatingPct, String fdv) {
}
