package com.vein.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vein.billing.EntitlementsDto.Status;

/**
 * 순수 단위(DB 불필요). 티어→한도 매핑과 정규화를 고정한다.
 */
class EntitlementsTest {

    @Test
    void freeTierHasFiniteLimits() {
        Status s = Entitlements.forTier("FREE");
        assertThat(s.tier()).isEqualTo("FREE");
        assertThat(s.pro()).isFalse();
        assertThat(Entitlements.scannerRuleLimit("FREE")).isEqualTo(3);
        assertThat(Entitlements.alertLimit("FREE")).isEqualTo(10);
    }

    @Test
    void proTierIsUnlimited() {
        Status s = Entitlements.forTier("PRO");
        assertThat(s.tier()).isEqualTo("PRO");
        assertThat(s.pro()).isTrue();
        assertThat(Entitlements.scannerRuleLimit("PRO")).isEqualTo(-1);
        assertThat(Entitlements.alertLimit("PRO")).isEqualTo(-1);
    }

    @Test
    void unknownOrNullTierNormalizesToFree() {
        assertThat(Entitlements.normalize(null)).isEqualTo("FREE");
        assertThat(Entitlements.normalize("gold")).isEqualTo("FREE");
        assertThat(Entitlements.forTier("gold").tier()).isEqualTo("FREE");
    }

    @Test
    void caseInsensitiveProDetection() {
        assertThat(Entitlements.isPro("pro")).isTrue();
        assertThat(Entitlements.isPro("Pro")).isTrue();
        assertThat(Entitlements.isPro("free")).isFalse();
    }

    @Test
    void forTierReflectsUsageCounts() {
        Status s = Entitlements.forTier("FREE", 2, 5);
        var rules = s.features().stream()
                .filter(f -> f.key().equals(Entitlements.SAVED_SCANNER_RULES)).findFirst().orElseThrow();
        assertThat(rules.used()).isEqualTo(2);
        assertThat(rules.limit()).isEqualTo(3);
        var alerts = s.features().stream()
                .filter(f -> f.key().equals(Entitlements.ALERTS)).findFirst().orElseThrow();
        assertThat(alerts.used()).isEqualTo(5);
    }

    @Test
    void forTierExposesFeatureLimits() {
        Status s = Entitlements.forTier("FREE");
        assertThat(s.features()).extracting(EntitlementsDto.Feature::key)
                .contains(Entitlements.SAVED_SCANNER_RULES, Entitlements.ALERTS);
        var rules = s.features().stream()
                .filter(f -> f.key().equals(Entitlements.SAVED_SCANNER_RULES)).findFirst().orElseThrow();
        assertThat(rules.limit()).isEqualTo(3);
    }
}
