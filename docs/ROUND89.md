# VEIN Round 89

Date: 2026-09-13

## 홈 "오늘의 주목 신호" 카드에 과거 적중률 칩 (FE)

자율 루프 R89. R86이 신호 **상세**에 패턴 base-rate를 붙였다면, R89는 그 판단 근거를
한 단계 앞당겨 **큐레이션 카드**에서 바로 보이게 한다. 홈 "오늘의 주목 신호"는 6개
후보를 올려주는데, 어떤 패턴이 과거에 실제로 먹혔는지는 카드를 하나씩 열어봐야만 알 수
있었다. triage(무엇을 먼저 열까)의 핵심 숫자인 적중률을 목록에서 바로 준다.

### 바뀐 것 (프론트 전용)

- `components/signal-card.tsx` — `SignalCardPerfHint{hitRate,sampleSize}` opt-in prop 추가.
  탐지 시각 옆에 "과거 적중 X% (nN)" 칩을 렌더하되 **표본 ≥10일 때만**(표본 부족
  적중률은 노이즈라 숨김). prop 미전달 시 기존과 동일(스캐너·종목상세 목록은 그대로 깨끗).
- `components/top-signals.tsx` — `/signals/performance/summary?horizon=1d`를 **섹션에서 1회만**
  조회해 `type|market|timeframe` 맵을 만들고 각 카드에 매칭 힌트를 넘긴다(카드별 재요청 없음,
  쿼리 캐시 공유).

### 왜 opt-in인가

- 적중률은 카드를 조밀하게 만든다. triage가 실제로 필요한 **큐레이션 면(홈 주목신호)**
  에서만 켜고, 대량 목록(스캐너)·이력성 목록(종목상세 최근신호)은 기존대로 둔다.
  스캐너는 이미 상단 성과 스트립(필터 패턴)이 있어 중복도 아니다.

### 검증

- FE: `tsc --noEmit`·`next build`(실데이터) 통과.
- mock 빌드(3100) 라이브 Chrome full smoke **0에러**.
- 홈 캡처 확인: 비트코인(ABC·CRYPTO·4h) `과거 적중 61.9%(n42)`, NVIDIA(TOP·US·1d)
  `47.4%(n19)`, 솔라나(ABC·CRYPTO·1d) `57.1%(n28)`, 삼성전자(IMALOL·KOSPI·1d)
  `65.2%(n23)`. Tesla(TOP·US·3d)는 성과 표본 없음 → 칩 미표시(노이즈 숨김 정상 동작).
- 검증 후 실데이터 빌드로 원복.
</content>
