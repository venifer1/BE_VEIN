package com.vein.market.provider.binance;

/** Raw Binance {@code /api/v3/ticker/price} row. Stays inside the provider package. */
record BinancePrice(String symbol, String price) {
}
