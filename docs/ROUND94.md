# VEIN Round 94

Date: 2026-09-13

## API_CONTRACT 잔여 미문서 엔드포인트 정리 (문서, R92 감사 후속)

자율 루프 R94. R92에서 paper/backtest/strategy를 문서화하며 "잔여 미문서 엔드포인트
전수 감사" 여지를 남겼고, 실제로 전 엔드포인트(~89개)를 컨트롤러 매핑과 대조해 계약서에
빠진 것들을 마저 채웠다. 코드 무변경.

### 반영 (컨트롤러 정적 대조)

- **market(홈)** 누락분 5개: `/market/indices/{key}/history`(?days), `/market/fear-greed/history`
  (?days), `/market/movers`(?market&type&limit), `/market/trending`, `/market/global`.
- **파생 신설 §6-1**: `/derivatives`(?limit), `/derivatives/{symbol}`(롱숏·OI 히스토리),
  `/liquidations`(?limit&min_notional&symbol), `/liquidations/summary`(?symbol) — 그동안
  계약서에 파생/청산 섹션 자체가 없었음. 펀딩비·OI 병기 설명 포함.
- **알림 웹푸시** 2개: `/notifications/web-push/config`(VAPID 공개키),
  `/notifications/web-push/subscription`(구독 등록).
- 확인: `GET /me`는 이미 §1에 문서화돼 있었음(추가 불필요).

출처: `MarketController`, `DerivativesController`, `LiquidationController`,
`NotificationController`.

### 검증

코드 무변경(문서만). 각 경로·쿼리 파라미터를 실제 `@GetMapping`·`@RequestParam`과 대조.
이로써 주요 엔드포인트는 API_CONTRACT에 반영 완료(단일 진실 소스 갭 해소).
</content>
