# VEIN Round 41

Date: 2026-09-08

## 오늘의 주목 신호 — 상위 점수 큐레이션 (Track A #3, 신호 과다 완화)

PROJECT_STATUS §4 Track A #3 "실사용 마찰 제거"의 구체 항목 **신호 과다**를 다룬다. 신호
목록은 시간순 커서 페이지네이션만 있어, ~400개 신호에서 좋은 후보를 찾으려면 계속 스크롤해야
했다. 시스템이 후보를 먼저 올려준다는 제품 핵심 가치(§1)가 홈에서 약했다.

### 추가된 것

**백엔드 (`com.vein.signal`)**
- `PatternSignalRepository.findTopByScore(market, Pageable)` — 활성 상태
  (DETECTED / NEAR_COMPLETION) + score non-null 신호를 **Pattern Score 내림차순**
  (동점 시 최신순)으로. 시장 스코핑 선택.
- `SignalService.top(market, limit)` — 상위 N개를 카드 DTO로. limit 기본 10·최대 30,
  instrument 배치 로드.
- `SignalController` — `GET /signals/top?market=&limit=` (`/{id}` 매처보다 먼저 매핑).

**프론트 (FE_VEIN)**
- `useTopSignals(market, limit)` 훅 + `TopSignalsSection` → 홈 상단(국면/캘린더 아래)
  "오늘의 주목 신호": 시장 탭(전체/코인/미국/코스피/코스닥) + 상위 6개를 기존 `SignalCard`로,
  "스캐너 전체 →" 링크. 데이터 없으면 안내 문구.
- mock `/signals/top` 계약(활성 신호 score 내림차순 slice).

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- 프론트 `tsc --noEmit` 0에러, 프로덕션 `build` 성공.
- **라이브 Chrome smoke(mock) 전 라우트 0에러.** 홈 "오늘의 주목 신호"에 점수순 카드 + 시장
  탭 렌더, 카드→신호 상세 딥링크 동작 — 스크린샷 확인. 검증 후 실데이터로 재빌드 복원.

### 남은 것 / 주의

- 상위 신호는 **현재 저장된 Pattern Score** 기준(구조/거래량/추세/변동성/뉴스 5요소). 신호
  성과(실측 적중률) 가중은 후속 여지.
- 스캐너 목록 자체의 점수 정렬/최소점수 필터는 커서 페이지네이션(시간순)과 얽혀 이번엔 미포함 —
  별도 라운드 후보.
