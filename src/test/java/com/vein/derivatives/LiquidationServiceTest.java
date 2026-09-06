package com.vein.derivatives;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vein.notification.NotificationService;
import com.vein.user.User;
import com.vein.user.UserRepository;
import com.vein.user.UserStatus;

class LiquidationServiceTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LiquidationService service = new LiquidationService(
            new ObjectMapper(), mock(SimpMessagingTemplate.class),
            notificationService, userRepository, 1800);

    @Test
    void normalizesUsdMForceOrderAndComputesNotional() {
        service.accept("""
                {
                  "e":"forceOrder",
                  "E":1568014460893,
                  "o":{
                    "s":"BTCUSDT",
                    "S":"SELL",
                    "q":"0.014",
                    "p":"9910",
                    "ap":"9910",
                    "X":"FILLED",
                    "z":"0.014",
                    "T":1568014460893
                  },
                  "ps":"BTCUSDT",
                  "st":1
                }
                """);

        LiquidationService.Snapshot snapshot = service.snapshot(10, BigDecimal.ZERO, null);
        assertThat(snapshot.count()).isEqualTo(1);
        assertThat(snapshot.totalNotionalUsd()).isEqualTo("138.740");
        assertThat(snapshot.longLiquidationUsd()).isEqualTo("138.740");
        assertThat(snapshot.events().getFirst().base()).isEqualTo("BTC");
        assertThat(snapshot.events().getFirst().positionSide()).isEqualTo("LONG");
    }

    @Test
    void filtersCoinMAndSmallEvents() {
        service.accept("""
                {"e":"forceOrder","E":1,"st":2,
                 "o":{"s":"BTCUSD_PERP","S":"BUY","q":"10","p":"100","ap":"100","z":"10"}}
                """);
        service.accept("""
                {"e":"forceOrder","E":2,"st":1,
                 "o":{"s":"ETHUSDT","S":"BUY","q":"1","p":"100","ap":"100","z":"1"}}
                """);

        assertThat(service.snapshot(10, new BigDecimal("1000"), null).events()).isEmpty();
        assertThat(service.snapshot(10, BigDecimal.ZERO, "ETH").events()).hasSize(1);
        assertThat(service.snapshot(10, BigDecimal.ZERO, "BTC").events()).isEmpty();
    }

    @Test
    void aggregatesWindowsAndDetectsSpike() {
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        service.accept(frame("BTCUSDT", "SELL", "200000", now.minusSeconds(60)));
        service.accept(frame("ETHUSDT", "BUY", "10000", now.minusSeconds(10 * 60)));
        service.accept(frame("SOLUSDT", "SELL", "10000", now.minusSeconds(50 * 60)));

        LiquidationService.Aggregation aggregation = service.aggregation(null, now);

        assertThat(aggregation.windows().get(0).count()).isEqualTo(3);
        assertThat(aggregation.windows().get(0).totalNotionalUsd()).isEqualTo("220000");
        assertThat(aggregation.windows().get(1).partial()).isTrue();
        assertThat(aggregation.spike().level()).isEqualTo("HIGH");
        assertThat(aggregation.spike().ratio()).isEqualTo("110.00");
    }

    @Test
    void sendsSpikeNotificationToApprovedUsersWithCooldown() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(userRepository.findByStatus(UserStatus.APPROVED)).thenReturn(java.util.List.of(user));
        Instant now = Instant.parse("2026-06-14T12:00:00Z");

        service.accept(frame("ETHUSDT", "BUY", "10000", now.minusSeconds(10 * 60)));
        service.accept(frame("BTCUSDT", "SELL", "200000", now.minusSeconds(60)));
        service.accept(frame("SOLUSDT", "BUY", "210000", now.minusSeconds(30)));

        verify(notificationService, times(1)).createSystemInApp(
                eq(7L), eq("Liquidation spike HIGH"), anyString());
    }

    private static String frame(String symbol, String side, String notional, Instant eventAt) {
        return """
                {"e":"forceOrder","E":%d,"st":1,
                 "o":{"s":"%s","S":"%s","q":"1","p":"%s","ap":"%s","z":"1","X":"FILLED"}}
                """.formatted(eventAt.toEpochMilli(), symbol, side, notional, notional);
    }
}
