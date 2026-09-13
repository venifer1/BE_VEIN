# VEIN Round 91

Date: 2026-09-13

## 모의투자 주문 내역에 종목 심볼 노출 (BE+FE)

자율 루프 R91. 모의투자 "내역" 탭의 주문이 종목을 **`#{instrument_id}`(숫자 id)**로 표시해
"BUY #1"처럼 보였다 — 내 매매 이력을 알아볼 수 없었다(포지션 탭은 symbol을 이미 보여줬는데
주문 내역만 빠져 있었음). `OrderResponse` DTO에 symbol이 없던 게 원인.

### 바뀐 것

**백엔드**
- `PaperDto.OrderResponse`에 `symbol`·`name` 추가(instrumentId 뒤).
- `PaperTradingService.toOrder(order, fill, instrument)`가 심볼/이름을 채움(null-safe).
  - 주문 생성 경로: 이미 로드한 `instrument`를 전달.
  - 포트폴리오 `recent_orders`: 주문들의 instrumentId를 **배치 로드**해 매핑(N+1 회피,
    SignalService와 동일 패턴).

**프론트**
- `lib/types.ts` `PaperOrder`에 `symbol?`·`name?`.
- 주문 내역이 `o.symbol ?? #{instrument_id}`로 심볼 우선 표시(폴백은 기존 숫자).
- `lib/mockAdapter.ts` 주문 생성 시 order 객체에 `symbol`·`name` 추가(mock 패리티).

### 검증

- BE: `gradlew compileJava`·전체 test 그린(ASCII 경로, OrderResponse 필드 추가 회귀 없음).
- **라이브**(Docker+bootRun, 실데이터): 임시 유저로 모의계정 생성 후 `POST /paper/orders`(BTC
  SPOT BUY) → 응답 `symbol=KRW-BTC name=비트코인`, `GET /paper/portfolio` recent_orders[0]도
  동일. 검증 후 임시 유저·계정·주문·포지션·렛저 정리.
- FE: `tsc`·`next build`(실데이터) 통과, mock 빌드(3100) full smoke **0에러**, 내역 탭 캡처로
  "BUY KRW-BTC · FILLED · SPOT · 0.01 @ ..." 렌더 확인(기존 "BUY #1" → 심볼).
</content>
