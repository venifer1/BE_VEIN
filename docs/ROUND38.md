# VEIN Round 38

Date: 2026-09-06

## 틱띄기 실시간화 — Upbit WebSocket 수집기 (Track A #4)

`RestPollingScalpCollector`에 명시돼 있던 TODO("replace REST approximation with an
Upbit WebSocket collector ... register it as @Primary")를 이행했다. 틱띄기는 쓸 거면
정확해야 한다 — REST 폴링은 최근 체결 슬라이스를 샘플링할 뿐이라 TPS/micro-vol이
근사였다.

### 추가된 것

- **`WebSocketScalpCollector`** (`com.vein.scalp.collector`)
  - `wss://api.upbit.com/websocket/v1`에 지속 연결, 상위 N개 KRW 마켓의 `orderbook`+`trade`
    구독. Upbit는 **바이너리 프레임**으로 JSON을 보내므로 `onBinary`로 수신·재조립.
  - 마켓별 라이브 상태(최신 호가 레벨 + 롤링 체결 윈도우)를 인메모리 유지. `collect()`는
    네트워크 호출 없이 그 상태를 읽어 `MarketData`로 반환 → TPS/micro-vol이 연속 스트림
    기반 실측.
  - JDK `java.net.http.WebSocket`(무의존성). `@Scheduled` 재연결 틱 + keepalive ping.
    상류/파싱 실패는 빈 스냅샷으로 degrade하고 다음 틱에 self-heal(never throw).
  - `vein.scalp.ws-enabled=true`면 `@Primary`로 REST 폴러를 대체(같은 `ScalpCollector`
    계약). 기본은 REST 폴백 유지.
- `RestPollingScalpCollector` — TODO 주석을 "WS 대체 구현 존재, 이 빈은 폴백" 으로 현행화.
- application.yml `vein.scalp.ws-enabled`/`ws-url`/`reconnect-ms`.

### 검증 (라이브 Upbit)

- `SCALP_ENABLED=true SCALP_WS_ENABLED=true` 기동 → 로그 "Upbit scalp WS connected
  (50 markets)".
- `GET /scalp/ranking`이 WS 소스 실측 스코어 반환 — 예: KRW-RAY(score 72.4, tps 5.6,
  micro_vol 6.0, ob_imbalance 3.91, bid_wall), KRW-BTC(tps 1.8, ask_wall), freshness FRESH.
- tps가 0이 아닌 값으로 잡히는 것 = WS 체결 이벤트가 윈도우에 흐르고 있다는 증거.

### 운영 메모

- 프로덕션 상시 실시간을 원하면 `SCALP_ENABLED=true SCALP_WS_ENABLED=true`. 끄면 기존
  REST 근사로 자동 폴백 — 기능 회귀 없음.
