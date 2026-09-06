package com.vein.instrument;

/**
 * Public instrument representation (부록 C / 표 16).
 */
public record InstrumentDto(
        Long id,
        String symbol,
        String name,
        String exchange,
        String market,
        String quoteCurrency,
        String status) {

    public static InstrumentDto from(Instrument i) {
        return new InstrumentDto(
                i.getId(),
                i.getSymbol(),
                i.getName(),
                i.getExchange(),
                i.getMarket(),
                i.getQuoteCurrency(),
                i.getStatus());
    }
}
