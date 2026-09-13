package com.vein.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.vein.common.ApiException;

/**
 * 모의투자 금액/수량/수익률 문자열 표기 순수 로직 회귀 보호(R156).
 * money·qty=소수 8자리, pct=소수 4자리, 모두 HALF_UP 후 후행 0 제거(계약: 숫자는 String).
 */
class PaperTradingServiceTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void moneyScalesTo8AndStripsTrailingZeros() {
        assertThat(PaperTradingService.money(bd("100.50000000"))).isEqualTo("100.5");
        assertThat(PaperTradingService.money(bd("2"))).isEqualTo("2");
        assertThat(PaperTradingService.money(bd("0.00000001"))).isEqualTo("0.00000001");
        // 8자리 초과는 HALF_UP 반올림
        assertThat(PaperTradingService.money(bd("1.123456789"))).isEqualTo("1.12345679");
    }

    @Test
    void qtyBehavesLikeMoney() {
        assertThat(PaperTradingService.qty(bd("3.14000000"))).isEqualTo("3.14");
        assertThat(PaperTradingService.qty(bd("10"))).isEqualTo("10");
    }

    @Test
    void pctScalesTo4AndStripsTrailingZeros() {
        assertThat(PaperTradingService.pct(bd("10.2500"))).isEqualTo("10.25");
        assertThat(PaperTradingService.pct(bd("10"))).isEqualTo("10");
        assertThat(PaperTradingService.pct(bd("-5.5"))).isEqualTo("-5.5");
        assertThat(PaperTradingService.pct(bd("0.33335"))).isEqualTo("0.3334"); // HALF_UP
    }

    @Test
    void zeroRendersAsPlainZero() {
        assertThat(PaperTradingService.money(bd("0"))).isEqualTo("0");
        assertThat(PaperTradingService.pct(bd("0.0000"))).isEqualTo("0");
    }

    // --- 주문 입력 정규화(기본값·대소문자·트림·검증) (R163) ---

    @Test
    void normalizeSide_defaultsBuyAndValidates() {
        assertThat(PaperTradingService.normalizeSide(null)).isEqualTo("BUY");
        assertThat(PaperTradingService.normalizeSide("  ")).isEqualTo("BUY");
        assertThat(PaperTradingService.normalizeSide(" sell ")).isEqualTo("SELL");
        assertThatThrownBy(() -> PaperTradingService.normalizeSide("HOLD"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void normalizeType_defaultsMarketAndValidates() {
        assertThat(PaperTradingService.normalizeType(null)).isEqualTo("MARKET");
        assertThat(PaperTradingService.normalizeType("limit")).isEqualTo("LIMIT");
        assertThatThrownBy(() -> PaperTradingService.normalizeType("STOP"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void normalizeInvestmentType_defaultsSpotAndValidates() {
        assertThat(PaperTradingService.normalizeInvestmentType(null)).isEqualTo("SPOT");
        assertThat(PaperTradingService.normalizeInvestmentType("futures")).isEqualTo("FUTURES");
        assertThatThrownBy(() -> PaperTradingService.normalizeInvestmentType("MARGIN"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void normalizePositionSide_onlyForFuturesWithDefaultFromSide() {
        // 현물이면 항상 null
        assertThat(PaperTradingService.normalizePositionSide("LONG", "BUY", "SPOT")).isNull();
        // 선물 + 미지정 → side로 유추(BUY→LONG, SELL→SHORT)
        assertThat(PaperTradingService.normalizePositionSide(null, "BUY", "FUTURES")).isEqualTo("LONG");
        assertThat(PaperTradingService.normalizePositionSide("", "SELL", "FUTURES")).isEqualTo("SHORT");
        // 선물 + 명시 → 대소문자 무시
        assertThat(PaperTradingService.normalizePositionSide("short", "BUY", "FUTURES")).isEqualTo("SHORT");
        assertThatThrownBy(() -> PaperTradingService.normalizePositionSide("SIDEWAYS", "BUY", "FUTURES"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void leverage_oneForSpotCappedAt50ForFutures() {
        assertThat(PaperTradingService.leverage("10", "SPOT")).isEqualByComparingTo("1"); // 현물 무시
        assertThat(PaperTradingService.leverage(null, "FUTURES")).isEqualByComparingTo("1"); // 기본 1
        assertThat(PaperTradingService.leverage("10", "FUTURES")).isEqualByComparingTo("10");
        assertThat(PaperTradingService.leverage("50", "FUTURES")).isEqualByComparingTo("50"); // 경계 허용
        assertThatThrownBy(() -> PaperTradingService.leverage("51", "FUTURES"))
                .isInstanceOf(ApiException.class); // 50x 초과 거부
    }
}
