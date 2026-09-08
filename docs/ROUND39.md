# VEIN Round 39

Date: 2026-09-08

## 경제 캘린더 + 종목 실적발표 D-day + EVENT_RISK (Track A #1 완결)

R37(거시경제/시장국면 엔진)의 "남은 것"에 명시돼 있던 항목 — *경제 캘린더(FOMC·CPI·고용) /
종목 실적발표 D-day 라벨 + 이벤트 전후 EVENT_RISK 라벨로 신호 confidence 조정* — 을
구현해 Track A #1을 마감한다.

**왜 필요한가:** 개별 신호를 볼 때 "지금 이 종목/시장에 임박한 고위험 이벤트(금리결정·물가·
실적)가 있는가"를 매번 따로 확인하고 있었다. 이벤트 전후에는 변동성이 커져 기술적 패턴의
신뢰도가 떨어지는데, 그 인지를 자동화한다.

### 설계 결정 — keyless 우선 + 사이드카 실적 폴백

- **경제 캘린더는 keyless.** FOMC·CPI는 규칙 유도가 불가능해 **큐레이션 고정일 상수 테이블**,
  고용보고서(비농업, NFP)는 BLS 규칙상 **매월 첫째 금요일**이라 어떤 실행일에도 정확 생성.
  외부 키·HTTP 호출 없음. FRED/외부 경제 캘린더 API 연동은 후속 과제.
- **실적발표일만 사이드카(yfinance) 경유.** 미국·코스피·코스닥 종목 한정. 사이드카가 없거나
  실패하면 **조용히 null**로 폴백해 EVENT_RISK가 실적 항목을 붙이지 않는다(스텁-폴백 철학).
- **EVENT_RISK는 저장 점수를 바꾸지 않는다.** 읽기 시점에 `confidence_delta`(참고용 음수)와
  사람이 읽는 note만 부여. 임박 이벤트 없으면 null → UI 조용히 숨김. 절대 throw하지 않음.

### 추가된 것

**백엔드 (`com.vein.macro`)**
- `EconomicCalendarProvider` — 고위험 미 매크로 이벤트 창 조회(`window(today,back,fwd)`),
  FOMC/CPI 고정일 + NFP 규칙 생성, 오늘 근접(|D-day|) 순 정렬.
- `EarningsProvider` — 종목별 다음 실적발표일(주식만), 6h TTL 캐시(null도 캐시), 사이드카 폴백.
- `EventRiskService` — (market,symbol) → EVENT_RISK 평가. 매크로 D-1~D+1 HIGH,
  실적 D-7~D-day 창, 가장 임박한 항목이 레벨 결정. HIGH −10 / MEDIUM −5.
- `MacroDto` — `EconomicEvent`·`CalendarResponse`·`EventRisk`·`EventItem` 레코드.
- `MacroController` — `GET /api/v1/macro/calendar?days=14`.
- `EquitySidecarClient.fetchNextEarnings(market,symbol)` — 사이드카 `/equity/earnings` 호출.
- `SignalDetailDto`에 `event_risk`(nullable) 추가, `SignalService.detail`에서 배선.

**사이드카 (`sidecar/app.py`)**
- `GET /equity/earnings?market=&symbol=` — yfinance `.calendar` → `.get_earnings_dates`
  순으로 다음 실적발표일(≥오늘) 산출, 6h 캐시, 실패 시 `{"next_earnings_date": null}`.
  KR은 `.KS/.KQ` 접미사 자동 부여.

**프론트 (FE_VEIN)**
- `useMacroCalendar` 훅 + `EconomicCalendar` 컴포넌트 → 홈 상단(국면 배너 아래) "다가오는
  경제 이벤트": D-day 배지(D-1 적색·D-3 앰버·이후 회색) + 유형칩 + 제목 + 날짜.
- 신호 상세: `event_risk.active`면 "⚠ 이벤트 리스크 HIGH/MEDIUM" 배너 + note.
- `types.ts`(EconomicEvent/MacroCalendar/SignalEventRisk, SignalDetail.event_risk),
  mock 계약 parity(`/macro`·`/macro/calendar` + 상세 event_risk 합성).

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- 사이드카 `py_compile` 성공.
- 프론트 `tsc --noEmit` 0에러, 프로덕션 `build` 성공.
- **라이브 Chrome smoke(mock 빌드) 전 라우트 0에러.** 홈에 "다가오는 경제 이벤트"
  (D-1 CPI/D-4 고용/D-9 FOMC) 렌더, 신호 상세에 "⚠ 이벤트 리스크 HIGH" 배너
  ("D-1 미국 소비자물가(CPI) — … 신뢰도 하향 참고(-10)") 렌더 — 스크린샷 육안 확인.
- 검증 후 실데이터 설정(`.env.local` USE_MOCK=false)으로 재빌드해 운영 상태 복원.

### 남은 것 / 주의

- **경제 캘린더 고정일은 큐레이션 상수**(FOMC 8회/연·CPI 익월 중순) — 연 1회 갱신 필요.
  코드 상수 테이블(`EconomicCalendarProvider.FOMC`/`CPI`)만 고치면 됨. 실 경제 캘린더 API
  연동은 후속(FRED/외부).
- 실적발표일은 사이드카가 떠 있을 때만 채워짐. 사이드카 다운 시 EVENT_RISK는 매크로 이벤트만.
