# VEIN Round 37

Date: 2026-09-06

## 거시경제 / 시장국면 엔진 (Track A #1, 기획 R32 → 구현)

PROJECT_STATUS가 "최우선(Track A #1)"으로 두고도 **코드 0줄**이던 항목. 개별 신호를
볼 때마다 "지금 시장이 어떤 국면인지"를 매번 따로 확인하던 것을 자동화한다.

### 설계 결정 — keyless 우선 + FRED 선택 보강

- **국면(BULL/BEAR/RANGE/TRANSITION)은 내부 데이터만으로 항상 판정**된다. 이미 수집 중인
  나스닥 지수 추세(최근 ~20p 모멘텀)와 공포탐욕을 신호로 쓴다 → 외부 키 없이도 가동.
- **금리차·M2·달러인덱스는 FRED 키가 있을 때만** 채워진다(`FRED_API_KEY`). 없으면 해당
  블록 null, 국면 판정은 내부 신호로 계속 — 사이드카 스텁-폴백과 동일 철학. 상류 실패에
  절대 throw하지 않는다.
- 재무부 XML 등 keyless 금리 소스는 봇 차단/불안정으로 신뢰가 낮아, 금리 계열은 FRED로
  일원화(무료 키). 국면 판정의 핵심 경로가 키에 의존하지 않게 설계.

### 추가된 것 (`com.vein.macro`)

- `MacroService` — 국면 엔진. 각 신호(나스닥 추세/공포탐욕/금리차 역전/달러 방향)를
  BULLISH(+1)/BEARISH(−1)/NEUTRAL(0)로 채점, 합산 점수 ±2 이상이면 BULL/BEAR,
  방향 혼재면 TRANSITION, 그 외 RANGE. `signals[]`로 근거를 사람이 읽게 노출. ~10분 TTL 캐시.
- `FredProvider` — 키 없으면 HTTP도 치지 않고 빈 결과. DGS2/DGS3MO/DGS10·M2SL·DTWEXBGS.
- `MacroController` — `GET /api/v1/macro`(스냅샷), `GET /api/v1/macro/regime`(국면만). 인증 필요.
- `MacroDto`/`FredResponse`, application.yml `vein.macro.fred.*`.
- **FE** — `useMacro` 훅 + `RegimeBanner` 컴포넌트, 홈(터미널) 상단 배치. 국면 라벨(색상) +
  요약 + 근거 신호 칩 + 금리차(있으면). 데이터 미가용 시 조용히 숨김.

### 검증 (실데이터 기동)

- `/macro/regime` 200: 실 공포탐욕(18, 공포)만으로 RANGE("횡보 국면") 판정
- 합성 나스닥 상승(+5.49%) 주입 후 캐시 클리어 → EQUITY_TREND BULLISH + SENTIMENT
  BEARISH → **TRANSITION(신호 혼재)** 로 정확히 분기(합성행·임시유저 정리)
- FRED 미키: `yield_curve`/`m2`/`dxy` null, `sources=[FEAR_GREED]` — 우아한 생략 확인
- FE: 홈에서 국면 배너 "횡보 국면" 렌더, 콘솔에러 0 (헤드리스)

### 남은 것

- FRED 무료 키 발급 후 `FRED_API_KEY` 설정 → 금리차·M2·DXY 신호 활성화
- 경제 캘린더(FOMC·CPI) / 종목 실적발표 D-day 라벨(§15.1 후반부) — 별도 라운드
