package com.vein.macro;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FRED {@code /series/observations} response (subset). {@code value} is a String
 * because FRED encodes missing points as {@code "."}. Field names are single words,
 * so deserialization is independent of the naming strategy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FredResponse(List<Obs> observations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Obs(String date, String value) {
    }
}
