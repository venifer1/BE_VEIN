# VEIN Round 92

Date: 2026-09-13

## API_CONTRACT.md에 백테스트·전략·모의투자 섹션 추가 — 문서

자율 루프 R92. R91에서 모의투자 주문 DTO를 손대다가 **`/paper/**`·`/backtests/**`·
`/strategies/**` 엔드포인트가 계약서에 아예 없다**는 걸 발견했다(단일 진실 소스인데
실동작하는 큰 기능군이 통째로 누락). 구현/기획엔 있는데 계약 문서엔 없어 FE/APP가 참조할
근거가 없던 갭. 컨트롤러·DTO와 정적 대조로 신규 섹션 "4-1. 검증 도구"를 추가했다.

### 반영 (코드 정적 대조)

- `POST /backtests/run` — 요청 `{type,market?,timeframe?,targetPct,stopPct,horizon,periodDays?,
  feePct?,walkForward?,isRatio?}`, 응답 metrics/equity_curve/trades/walk_forward(과적합 경고).
- `GET/POST /strategies`, `GET/DELETE /strategies/{id}`, `POST /strategies/{id}/run`(성과 스냅샷),
  `GET /strategies/{id}/history`.
- `POST /paper/accounts`(create-or-reset), `POST /paper/orders`(SPOT/FUTURES·즉시체결·
  `OrderResponse`에 R91 symbol/name 포함 명시), `GET /paper/portfolio`, `GET /paper/performance`.
- 컴플라이언스 노트(가상 현금·실주문 없음, 신호 상세 액션 연결 R84).

출처: `PaperController`/`PaperDto`, `BacktestController`/`BacktestDto`, `StrategyController`/
`StrategyDto`.

### 검증

코드 무변경(문서만). 각 엔드포인트·필드를 실제 컨트롤러 매핑·DTO 레코드와 1:1 대조.
(향후 후속: derivatives 상세 등 잔여 미문서 엔드포인트가 더 있는지 전수 감사 여지.)
</content>
