package com.vein.condition;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public final class ConditionDto {

    private ConditionDto() {
    }

    public record Condition(String indicator, String operator, String value, String target) {
    }

    public record RunRequest(String market, String timeframe, String logic,
                             List<Condition> conditions) {
    }

    public record SaveRequest(String name, String market, String timeframe,
                              String logic, List<Condition> conditions) {
    }

    public record UpdateRuleRequest(Boolean enabled) {
    }

    public record Match(Long instrumentId, String symbol, String name, String market,
                        String price, String rsi14, String volumeRatio,
                        String ma5, String ma20, String macdHistogram,
                        List<String> matchedConditions) {
    }

    public record RunResponse(int evaluatedCount, int matchedCount, List<Match> items) {
    }

    public record SimulationResponse(Long ruleId, int evaluatedCount, int matchedCount,
                                     String matchRate, String frequencyGrade,
                                     List<Match> sampleItems) {
    }

    public record RuleResponse(Long id, String name, String market, String timeframe,
                               String logic, JsonNode conditions, boolean enabled,
                               String createdAt, RuleRunResponse latestRun) {
    }

    public record RuleRunResponse(Long id, int evaluatedCount, int matchedCount,
                                  String matchRate, String frequencyGrade,
                                  int notificationCount, String createdAt) {
    }
}
