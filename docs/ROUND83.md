# VEIN Round 83

Date: 2026-09-12

## 신호 카드에서 바로 관심 등록/해제 (FE, 마찰 감소)

자율 루프 R83. 핵심 사용 루프는 "시스템이 올린 후보를 보고 → 관심종목에 담아 추적"인데,
지금까지 관심 등록은 **신호 상세를 열어야만** 가능했다(홈 "오늘의 주목 신호"·스캐너 목록에서
바로 담을 수 없음). 후보를 훑다가 담는 흔한 동작에 매번 상세 왕복이 끼어 마찰이 됐다.

### 바뀐 것 (FE 전용)

- `components/signal-card.tsx` — 카드 우측(체브론 앞)에 **관심 등록 별 토글** 추가. 담겨 있으면
  채운 별(경고색), 아니면 빈 별. `useWatchlist`로 소속 판정, `useAddWatchItem`/`useRemoveWatchItem`
  로 토글. 카드가 `<Link>`라 별 클릭이 상세로 네비게이션 타지 않도록 `preventDefault`+
  `stopPropagation`, 뮤테이션 진행 중 비활성. `aria-label`/`aria-pressed`로 접근성.
- SignalCard는 홈 top·스캐너 목록·종목 상세 "최근 신호" 3곳 공유라 한 번에 적용된다.
- 워치리스트 쿼리는 react-query 키(`["watchlist"]`)로 카드 간 공유(dedupe)라 목록이 카드마다
  재요청하지 않는다. mock 어댑터도 GET/POST/DELETE 모두 지원(패리티 확인).

### 검증

- 백엔드/계약 무변경. `tsc --noEmit`·`next build` 통과.
- 워치리스트 왕복 실측: `POST /watchlists/default/items`(201)→목록 존재→`DELETE`(204)→0
  (검증 후 원복). 카드 별이 호출하는 경로와 동일.
- FE 재기동 후 라이브 Chrome full smoke **0에러**, 홈 스크린샷에서 각 주목신호 카드에 별 렌더 확인.
