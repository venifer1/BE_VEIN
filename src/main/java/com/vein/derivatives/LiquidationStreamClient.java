package com.vein.derivatives;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort Binance USD-M all-market liquidation WebSocket client.
 */
@Component
@Slf4j
public class LiquidationStreamClient implements DisposableBean {

    private final LiquidationService service;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final boolean enabled;
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile WebSocket socket;

    public LiquidationStreamClient(
            LiquidationService service,
            @Value("${vein.liquidation.ws-url:"
                    + "wss://fstream.binance.com/market/ws/!forceOrder@arr}") String wsUrl,
            @Value("${vein.liquidation.enabled:false}") boolean enabled) {
        this.service = service;
        this.endpoint = URI.create(wsUrl);
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Scheduled(fixedDelayString = "${vein.liquidation.reconnect-ms:5000}")
    public void ensureConnected() {
        if (!enabled || socket != null || !connecting.compareAndSet(false, true)) {
            return;
        }
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(endpoint, new Listener())
                .whenComplete((ws, error) -> {
                    connecting.set(false);
                    if (error != null) {
                        service.setConnected(false);
                        log.warn("Binance liquidation stream connect failed: {}", error.getMessage());
                    }
                });
    }

    @Override
    public void destroy() {
        WebSocket current = socket;
        socket = null;
        service.setConnected(false);
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder frame = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            service.setConnected(true);
            log.info("Binance liquidation stream connected");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            frame.append(data);
            if (last) {
                service.accept(frame.toString());
                frame.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            disconnected(webSocket, "closed " + statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            disconnected(webSocket, "error " + error.getMessage());
        }

        private void disconnected(WebSocket webSocket, String reason) {
            if (socket == webSocket) {
                socket = null;
            }
            service.setConnected(false);
            log.warn("Binance liquidation stream {}", reason);
        }
    }
}
