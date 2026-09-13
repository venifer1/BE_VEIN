# VEIN — 프로젝트 현황

> **기준일:** 2026-09-13 (R88 통계 리프레시)
> **문서 성격:** 이 프로젝트가 왜 존재하고, 어디까지 왔고, 다음에 뭘 할지에 대한 단일 기준 문서.
> 라운드별 상세 이력은 `WORKLOG.md`·`ROUND*.md`, API 계약은 `API_CONTRACT.md` 참조.

---

## 0. 왜 만드는가 — 이 프로젝트의 제1 전제

**내가 쓰려고 만든다.**

기존에 직접 만들어 쓰던 데스크톱 터미널(`veni_invest_abc`, Python/Tkinter, 11탭)이 실제로
매일 쓰이고 있는데, **데스크톱에 묶여 있다는 것 하나가 결정적 제약**이다. 장중에 자리에
없으면 아무것도 못 본다. VEIN은 그 터미널을 모바일로 옮기고, 옮기는 김에 데스크톱에서는
구조상 못 하던 것(서버 상시 스캔, 푸시 알림, 신호 성과 누적)을 붙이는 작업이다.

### 이 전제가 실제로 바꾸는 것

| | 상용 제품이었다면 | 실제 (개인 도구 우선) |
|---|---|---|
| 우선순위 기준 | "이게 팔리나" | **"내가 이번 주에 쓰나"** |
| 검증 방법 | 사용자 인터뷰·A/B | **내가 직접 쓰다가 불편하면 그게 버그** |
| 완성 기준 | 출시 게이트·SLA | **내 매매 루틴을 데스크톱 없이 돌릴 수 있는가** |
| 미완성 기능 | 출시 블로커 | 안 쓰면 그냥 안 쓰면 됨 |
| Flutter 앱 지연 | 심각한 문제 | **웹으로 충분하면 급하지 않음** |
| 사이드카가 레거시 저장소에 있음 | 배포 불가 수준 | 내 PC에서 도니까 당장은 문제 없음 |

### 반대로, 이 전제 때문에 생기는 위험

- **기획서 v1.2는 79개 섹션짜리 상용 SaaS 전제로 작성되어 있다.** 전략 마켓플레이스, 파트너
  거버넌스, 웹훅 계약, FinOps, 크리에이터 정산… 개인 도구 관점에서는 **대부분 과설계**다.
  기획서를 "해야 할 일 목록"으로 읽으면 영원히 안 끝난다. **참고 자료로 읽어야 한다.**
- 사용자가 나 하나면 **엣지케이스가 안 드러난다.** 남에게 보여주는 순간 쏟아질 것들이 지금은
  잠재해 있다.
- 혼자 쓰는 코드라 **문서·테스트를 건너뛰기 쉽다.** 실제로 이미 문서 드리프트가 발생했다(6장).

---

## 1. 제품 정의

**VEIN** — 코인/주식 **매매 인텔리전스 모바일 플랫폼** (현재: 내부 알파)

```
캔들 수집 → 패턴 탐지(ABC·고점판독·이말올) → 신호 저장 → 점수·설명(Explain)
   → 모바일 신호함·차트·알림 → 백테스트/모의투자로 검증 → 성과 누적
```

- **4개 시장**: 코인(업비트/바이낸스) · 미국주식 · 코스피 · 코스닥
- **핵심 가치**: 내가 종목을 찾아다니는 대신, **시스템이 후보를 먼저 올려준다**
- **컴플라이언스 선**: 전 화면 "투자 참고용 · 투자권유 아님" 고지. **자동매매·실거래 주문 없음**
- **브랜드**: `VEIN` (코드·백엔드 패키지 `com.vein`). `Veni Invest ABC`는 레거시 레퍼런스 구현

---

## 2. 어디까지 왔나

### 2.1 구성

| 저장소 | 스택 | 규모 | 상태 |
|---|---|---|---|
| **BE_VEIN** | Spring Boot 3.3 · Java 21 · PostgreSQL 16 · Redis 7 · Flyway | Java · 33 컨트롤러 · **약 89개 엔드포인트** · 마이그레이션 V1~V29 | 실동작 |
| **FE_VEIN** | Next.js 14 App Router · TS · Tailwind/shadcn · lightweight-charts · TanStack Query | TS 약 14,200줄 · 16 라우트 · **약 60개 엔드포인트 사용** | 실동작 |
| **APP_VEIN** | Flutter (Dart 3) · riverpod · go_router · dio · fl_chart | Dart 7,614줄 · 12 라우트 · **22개 엔드포인트 사용** | **크게 지연** |
| **사이드카** | Python · yfinance · pykrx · telethon · Upbit WS | — | **SIDECAR_VEIN 별도 저장소로 분리**(`requirements.txt`로 standalone 실행 가능). 실행은 레거시 venv도 허용 |

### 2.2 구현 완료 (코드로 확인됨)

- ✅ **인증/권한** — JWT + refresh 회전, 승인제 가입, SUPER_ADMIN
- ✅ **4시장 시세·캔들·지표** — RSI · MA(5/20/60/120) · 볼린저 · MACD (순수함수 `Indicators.java`)
- ✅ **패턴 탐지 3종** — ABC · TOP(고점판독) · IMALOL(이말올). 순수 Java, 골든 테스트 존재
  - ※ **TRIANGLE(삼각수렴)은 의도적으로 제거**(V13/V14로 DB까지 정리)
- ✅ **Pattern Score v1** — 완성도30·거래량20·추세20·변동성10·뉴스20 = 100점. **영속화(V29)+홈 "오늘의 주목 신호" 종합점수 정렬(R90)+카드 "종합 {점수}" 노출(R101)**
- ✅ **Explain** — 규칙기반 근거·위험·다음확인 + Risk Guard + 표본 Confidence + 유용성 피드백
- ✅ **신호 생애주기** — DETECTED → NEAR_COMPLETION → INVALIDATED / EXPIRED 상태 전이
- ✅ **신호 성과 추적** — 1h~7d 수익률 · MFE/MAE · 패턴별 적중률 요약
- ✅ **조건검색기** — RSI·거래량·MA·MACD 조합, AND/OR, 저장식, 스케줄 자동실행, 매칭 알림, 빈도 시뮬레이션
- ✅ **백테스트/전략** — 수수료·목표/손절/기간 청산 · 워크포워드 IS/OOS 과적합 경고 · 전략 저장·재실행
- ✅ **모의투자** — 현물/선물 · LONG/SHORT · 레버리지 · 부분청산 · 원장 · 신호→모의진입 연결
- ✅ **시장데이터** — 파생(펀딩/OI/롱숏) · 펀비차익(Bybit) · TVL(DefiLlama) · 유통량 · 테마/섹터 · **강제청산 스트림 + 급증 알림**
- ✅ **속보** — Bloomberg RSS + 텔레그램(코인니스) + 감성분석 + 종목 태깅·딥링크
- ✅ **알림** — 쿨다운 · 인앱 · **VAPID 백그라운드 웹푸시** · 전달 시도 추적
- ✅ **Admin** — 회원 승인/잠금 · 감사로그 · 알림/스캐너 운영지표
- ✅ **실시간** — STOMP/SockJS 가격 푸시 (opt-in, 미설정 시 REST 폴링 폴백)

### 2.3 데이터 규모 (실가동 기준)

캔들 약 **29.6만**(295,840) · 신호 약 **316** · 종목 약 **476** (미국 243 · 코인 265 등)
*(캔들·신호는 2026-09-13 R90/R101 라이브 DB 실측. 종목 수는 미재확인 — 이전 값 유지.)*

### 2.4 최근 진행 이력

| 라운드 | 내용 |
|---|---|
| R24 | 저장 조건검색 빈도 시뮬레이션 |
| R25 | 청산 집계·급증 감지 |
| R26 | **Admin 대시보드** 신설 |
| R27 | **백그라운드 웹푸시**(VAPID) 완성 |
| R28 | Admin 회원 관리 · 감사로그 |
| R29 | 저장 조건검색 **자동 알림**(스케줄 평가) |
| R30 | 청산 급증 알림(쿨다운·에스컬레이션) |
| R31 | 모의투자 UI 개선 + 신호→액션 연결 + 전 화면 한글 문구 정리 |
| R32 | **거시경제/시장국면 엔진 기획 추가** — *기획만, 코드 없음* |
| R33 | **보안 정리** — 시크릿 전면 외부화, 4종 자격증명 회전, git 히스토리 정리 |
| R34 | **비로그인 공개 리포트** — 주간 패턴 성과 리포트(JSON/MD) + `/public/**` 개방 + IP 레이트리밋 (MONETIZATION 단계2 ②) |
| R35 | **공개 회원가입** — `POST /auth/signup`(즉시 승인·자동로그인 토글) + 유입추적 컬럼(V23) + 가입 레이트리밋 (MONETIZATION 단계2 ①·③) |
| R36 | **FE 단계2 마감** — 비로그인 랜딩(`/landing`, 실측 적중률+가입폼+리포트 링크) + 후원 링크 (MONETIZATION 단계2 ④·⑤). **단계2 코드 5종 ①~⑤ 완료** |
| R37 | **거시경제/시장국면 엔진** (Track A #1) — `com.vein.macro`: 국면(BULL/BEAR/RANGE/TRANSITION) 내부 데이터로 판정 + FRED 선택 보강(금리차·M2·DXY) + `GET /macro[/regime]` + 홈 국면 배너 |
| R38 | **틱띄기 실시간화** (Track A #4) — `WebSocketScalpCollector`: Upbit WS(orderbook+trade) 지속 연결로 TPS/micro-vol 정확 산출, `ws-enabled` 시 @Primary로 REST 폴러 대체 |
| R39 | **경제 캘린더 + 실적발표 D-day + EVENT_RISK** (Track A #1 완결) — `com.vein.macro`: keyless 경제 캘린더(FOMC/CPI 고정일 + NFP 규칙) `GET /macro/calendar`, 실적발표일(사이드카 yfinance `/equity/earnings`, 폴백), 신호 상세 `event_risk` 라벨(읽기 시점 confidence 힌트) + 홈 캘린더/신호 배지 |
| R40 | **데이터 출처 가시화 + 사이드카 재현성 마감** (Track A #2·B #3) — `/system/status`에 provider별 `source`(REAL/STUB) + `sidecar{healthy,url}` 추가(`SidecarHealth` 30s 캐시). 사이드카 다운 시 yfinance/pykrx/telegram=STUB. FE 설정 "데이터 출처"에 실데이터/합성 배지 + 스텁 경고 배너. 사이드카 README standalone venv 우선으로 개정 |
| R41 | **오늘의 주목 신호** (Track A #3, 신호 과다 완화) — `GET /signals/top?market=&limit=`(활성 신호 Pattern Score 상위), 홈 "오늘의 주목 신호" 섹션(시장 탭 + 상위 6 카드). 400여 신호를 뒤지지 않고 후보를 먼저 노출 |
| R42 | **조용한 시간(Quiet Hours)** (Track A #3, 알림 노이즈 완화) — V24 `notification_prefs`, `GET/PUT /me/notification-prefs`, 지정 KST 시간대(자정 넘김 지원) 동안 새 알림 생성 스킵(쿨다운 미진전). 설정 "조용한 시간" 카드 |
| R43 | **견고성: 잘못된 요청 4xx 정규화** (Track B #4) — `GlobalExceptionHandler`가 나쁜 파라미터/enum·누락 파라미터·깨진 JSON·잘못된 메서드를 500이 아닌 **400/405 + 에러 봉투**로 매핑. `ErrorCode.METHOD_NOT_ALLOWED`. 조용한 시간 값 0-23 검증. (소유권/IDOR은 이미 견고함을 감사로 확인). 별개로 `AbcDetectorTest` 1건 선행 실패 발견 → R44에서 수정 |
| R44 | **ABC 탐지기 버그 수정** — 0→A 하락 레그에 "A보다 낮은 저점 없음(`breaksBelow`)" 가드 추가. 평탄부의 얕은 2차 저점이 A로 뽑혀 최근-B dedup이 올바른 파동을 밀어내던 버그. `algorithm_version` 1.0.2. **ASCII 경로 전체 테스트 그린(선행 실패 해소)** |
| R45 | **알림 다이제스트(읽기 시점 요약)** (Track A #3, 알림 노이즈 완화) — `GET /notifications/digest?window=`(창 안 알림을 SIGNAL/SCANNER/LIQUIDATION/SYSTEM 분류별 집계 + 안읽은 최신 표본 + 요약 문장). 순수 집계기 `NotificationDigest`(단위 5/5). 설정 "알림 요약" 카드. 별개로 클린 DB 부팅 중 **R42 잔여 스키마 드리프트 발견 → V25**(`notification_prefs` 시각 컬럼 int2→int4, 엔티티 `int`와 validate 충돌 해소) |
| R46 | **알림 스풀링(조용한 시간 보류 후 방출)** (Track A #3 마무리) — V26 `notifications.held_until`. R42가 조용한 시간에 알림을 **드롭**(영구 소실)하던 것을, `held_until`(창 종료 시각)까지 **보류**했다가 창이 끝나면 읽기 모델에 자연 노출(lazy-release, 스케줄러 없음). 목록·안읽음·다이제스트가 `held_until<=now`만 활성 취급, 보류 시 웹푸시 스킵. `AlertEvaluationService` 드롭→보류. `windowEnd` 순수 정적(단위 6/6). 설정 "조용한 시간" 문구 정정 |
| R47 | **차트 조작감** (Track A #3 마지막) — `chart-view.tsx`(lightweight-charts)가 팬/줌·과거캔들 로드·리사이즈마다 차트를 **통째 재생성**하던 것을, **마운트 1회 생성 + 부분 갱신 effect**로 리팩터(동적 시리즈·가격선 ref 추적, 콜백 ref화). 크로스헤어 OHLC 레전드(날짜·시고저종·등락%), 모바일 터치/핀치/키네틱 튜닝(vertTouchDrag off로 페이지 스크롤 보존). 백엔드/계약 무변경. FE typecheck/build/smoke 0에러 |
| R48 | **온보딩 "시작하기" 체크리스트** (Track B #2) — `GET /me/onboarding`(관심종목·알림·모의투자·조건검색 스텝 완료를 **실제 데이터에서 파생** + 진행률) + `POST /dismiss`(V27 `users.onboarding_dismissed_at`). `OnboardingSteps` 순수 조립(단위 5/5). 홈 상단 "시작하기" 카드(완료=취소선·미완료=딥링크, all_done·dismissed면 숨김). Track B 첫 라운드 |
| R49 | **엣지케이스: 미매핑 경로 404 정규화** (Track B #4) — 신규 유저 GET 훑기 실측으로 **인증 통과 미매핑 경로가 전부 500**(catch-all이 `NoResourceFoundException`을 삼켜 ERROR 로그+500)임을 발견. `GlobalExceptionHandler`에 `NoResourceFound/NoHandlerFound→404 NOT_FOUND` 핸들러 추가(R43 보완). 단위 6/6. 인접 엣지(bad path var·cursor·window)는 R43로 이미 견고함을 실측 확인 |
| R50 | **데이터 출처 사용지점 노출** (Track B #3) — R40이 설정 한 곳에만 REAL/STUB를 보여주던 것을, 기존 `/system/status`를 사용 지점에서 읽어 확장. 전역 배너(`DataSourceBanner`, 사이드카 다운/STUB 시 상단 상시 "합성값" 경고+설정 링크) + 종목 상세 `MarketStubBadge`(미국 yfinance·국내 pykrx 합성 시 "합성" 배지). 프론트 전용, 백엔드/계약 무변경 |
| R51 | **다이제스트 조용한시간 방출 요약** (Track A #3 후속) — R46 스풀링으로 보류됐다 방출된 건수를 R45 다이제스트에 `released`로 집계(읽기 시점, 스케줄러 없음). 설정 "알림 요약"에 "보류됐다 방금 도착 N건" 라인. 단위 6/6 |
| R52 | **Track C 착수: 구독 티어 + 엔타이틀먼트** — V28 `users.tier`(FREE/PRO). `com.vein.billing`: `Entitlements`(순수 티어→한도), `GET /me/entitlements`, 게이트 1개(FREE 저장식 3개 초과 시 402 PLAN_LIMIT_EXCEEDED). 설정 "구독" 카드. **결제 연동·기능제한 전면적용은 후속**. 단위 5/5 |
| R53 | **저가-이탈 무효화 완화 버퍼** (사용자 요청) — ABC(A 저점)·TOP(B 저점) 무효화가 1틱만 깨도 죽던 것을, `low < 기준선×(1−buffer)`일 때만 무효로 완화(꼬리/노이즈 흡수). `vein.signal.invalidation-buffer-pct` 기본 3%(env로 조정). 순수 `breaches()` 단위 5/5. FE 무변경 |
| R54 | **스캐너 기본 활성 신호만** (UI 감사) — 스캐너 목록이 상태 무필터라 만료 신호가 상단 노이즈이던 것을, `GET /signals?active_only=` 추가 + 스캐너 기본 활성만(DETECTED/NEAR_COMPLETION), "만료 포함" 토글로 전체 노출. API 하위호환(기본 false). mock 패리티 |
| R55 | **UI 감사 후속 정리** (FE) — 전 화면 점검(화면+코드) 후 실수정: 모의 초기잔액 천단위 콤마+한글단위 힌트, 신호 strip "표본 부족" 경고(표본<20), 파괴적 확인(조건검색식 삭제·admin 잠금). 대부분 화면은 이미 깔끔함을 확인(파생 빈박스는 로딩 스켈레톤 오탐). 백엔드 무변경 |
| R56 | **구독 티어 실사용화** (Track C) — R52 미완결 마감: 알림 한도 FREE 실집행(10개 초과 402), `PATCH /admin/users/{id}/tier`(FREE/PRO, 감사로그)로 결제 없이 PRO 부여/회수. `UserDto.tier`, admin 티어 토글 UI. 라이브 검증(11번째 알림 402→PRO 201). 결제 PG는 여전히 후속 |
| R57 | **조건검색 저장 에러 표면화 + 틱띄기 자릿수** (FE 폴리시) — 조건검색식 저장 실패(특히 FREE 402 한도)가 화면에 안 뜨던 것을 저장 버튼 아래 메시지로 노출. 틱띄기 지표(스프레드/TPS/마이크로변동/불균형) 2자리 고정. 백엔드 무변경 |
| R58 | **조건검색 null 필드 500 누출 수정** (실측) — `POST /scanner/rules|run`에 빈/부분 바디(`{}`)가 `Set.of(...).contains(null)` NPE로 500 나던 것을, market/logic/indicator/operator null 선(先)가드로 400 정규화(R43 바디-검증 빈틈). FE 무변경 |
| R59 | **다이제스트 기간 토글(24시간/7일)** (FE+BE 폴리시) — 설정 알림요약에 24시간/7일 토글(기존 `?window=` 활용), 요약 라벨을 "N일"로(168→"최근 7일", 24는 "24시간" 유지). 단위 7/7. + 넓은 엣지-입력 프로브로 잔여 500 없음 확인 |
| R60 | **엔타이틀먼트 사용량(used)** (Track C) — `GET /me/entitlements` 각 기능에 현재 사용 수 추가(저장식/알림 countByUserId 재사용). 구독 카드가 "사용/한도"(예: 2/3개) 표시 + 한도 도달 앰버 강조. 402 맞기 전 잔여 파악 가능. 단위 7/7 |
| R61 | **스캐너 "관심종목만" 필터** (FE) — 백엔드 `?watchlist_only=`를 스캐너 필터바 토글로 노출(URL `watch=true`). 관심종목 담긴 종목의 신호만 보는 뷰. 기존 API 활용, 백엔드 무변경 |
| R62 | **알림 규칙 삭제** (기능 갭) — 알림에 삭제가 없어 FREE 10개 한도에 갇히던 것을 `DELETE /alerts/{id}` 추가(소유자만·404 IDOR-safe, FK 언링크로 알림 이력 보존). 설정 알림카드 삭제 버튼(확인). 라이브 200→404→0 |
| R63 | **알림 "모두 읽음"** (편의 갭) — 개별 읽음만 있던 알림함에 `POST /notifications/read-all`(활성 안읽음만, 스풀 보류 제외) + 설정 알림함 "모두 읽음" 버튼. 라이브 2→0 |
| R64 | **모의 계정 리셋 UI** (R31 계획) — 계정 존재 시 리셋 경로가 없던 것을 모의화면 하단 "계정 리셋"(잔액 입력+confirm, 기존 create-or-reset 재사용)으로 추가. + 모의 주문 엣지 실측 전부 견고(잔액부족·보유초과·레버리지 상한 400, 500 없음) |
| R65 | **스캐너 결과 개수 + 필터 초기화 상시 노출** (FE) — 필터 활성 시 "필터 초기화"가 빈 결과에만 있던 것을 목록 상단(결과 개수와 함께)에 상시 노출. + 전략/백테스트 실측 전부 견고(400/404/200, 500 없음) |
| R66 | **신호 카드 C목표까지 거리(%)** (FE) — ABC/TOP 카드에 `C {가격} (+X%)`로 현재가→C목표 거리 표시(기존 리스트 DTO current_price·c_target 활용). 한눈에 남은 상승/하락폭. 백엔드 무변경 |
| R67 | **신호 상세 리스크·리워드(R:R)** (FE, R66 후속) — 상세 "무효화 기준"에 목표까지/무효화까지 거리(%)+손익비(X.XX:1) 3줄(ABC/TOP, 현재가·C목표·무효화가 활용). 백엔드 무변경 |
| R68 | **한도 게이트 중앙화 + 테스트** (Track C 품질) — 알림·저장식 FREE 게이트의 인라인 중복(`limit>=0 && count>=limit`)을 `Entitlements.overLimit` 순수 헬퍼로 통합(경계 회귀 방지). 단위 8/8. 동작 동일(4번째 402 회귀 확인) |
| R69 | **스캐너 "관심종목만" 빈결과 안내** (FE, R61 후속) — 관심종목만 켰는데 결과 없으면 "관심종목 신호가 없습니다·종목 등록/필터 끄기" 전용 안내. 백엔드 무변경 |
| R70 | **알림 분류 마커 공유 상수화** (품질) — 다이제스트 카테고리 마커("Scanner match:"·"Liquidation spike")가 생성부/분류부에 중복돼 드리프트 위험이던 것을 `NotificationDigest` 공유 상수로 통합(ConditionScanner·Liquidation·Digest 참조). 단위 8/8, 동작 동일 |
| R71 | **오프라인 데모 mock landing 갭 수정** (Track B) — queries↔mockAdapter 정적 대조로 누락 발견: `/auth/signup`·`/public/reports/weekly` mock 핸들러 없어 mock 모드 랜딩(리포트·가입) 깨짐. 둘 다 추가, 실제 mock 빌드(포트 3100)로 랜딩·가입·로그인 검증. 백엔드 무변경 |
| R72 | **Admin 개요에 구독 티어 집계** (Track C 운영) — 티어 실사용화됐으나 운영자가 FREE/PRO 분포를 못 보던 것을, `admin/overview` users에 `byTier` 추가 + admin "구독 티어" 분포·PRO 수 노출. 라이브 `{FREE:2}` 확인 |
| R73 | **오프라인 데모 mock 전화면 검증 + 로그인 역할 일관성** (Track B) — mock 빌드(포트 3100) full smoke 0에러(전 화면 mock 핸들러 온전). mock 로그인/`/me` 역할을 usr_1과 일치하게 SUPER_ADMIN으로 수정(데모에서 관리화면 노출). 백엔드 무변경 |
| R74 | **API_CONTRACT.md 동기화** (Track B 문서) — 단일 계약 문서에 R45~R73 누락 8개 그룹 반영: active_only·scanner 한도/simulate/history·alerts DELETE·notifications read-all/digest·onboarding·§10 구독/운영(entitlements·tier·by_tier). 문서↔실제 정적 대조로 검증 |
| R75 | **조건검색 저장식 한도 선제 안내** (Track C UX) — FREE 3개 한도를 402 맞기 전에 표시: 조건검색 패널이 `/me/entitlements`로 `저장식 N/3` 배지·안내를 렌더, 한도 도달 시 저장 버튼 비활성+PRO 안내(→설정). 저장/삭제 시 entitlements 무효화로 설정 화면 사용량 동기화. FE 전용, 라이브 smoke 0에러 |
| R76 | **알림 규칙 한도 선제 안내** (Track C UX, R75 후속) — 알림 생성 다이얼로그가 `/me/entitlements`의 `ALERTS` 한도(FREE 10)를 열릴 때 조회, `N/10` 안내 상시 노출·한도 도달 시 폼 대신 PRO 안내 화면으로 전환(402 원천 차단)+`PLAN_LIMIT_EXCEEDED` 친화 메시지 매핑. FE 전용, 라이브 smoke 0에러 |
| R77 | **무효화 실질가(R53 완충) 노출** (BE+FE) — 신호 상세가 raw 기준선만 보여주던 걸, 실제 발동가(`effective_price`=기준선×(1−buffer))·완충률을 함께 노출. `SignalStatusTransitionService`에 `thresholdPrice`/`effectiveInvalidationPrice` 추출·`InvalidationDto` 확장, FE 카드에 "실질 무효화가(−3% 완충)"+설명, R:R 거리도 실질가 우선. `SignalLowBreakTest` 6/6, 실측 `/signals/275`={89.25→86.5725}, 라이브 smoke 0에러 |
| R78 | **비관리자 admin 접근 500 → 403 정규화** (BE, 견고성) — 신규 TESTER 토큰 GET 훑기 실측으로 `/admin/**` 전 엔드포인트가 비관리자에게 **500**(catch-all이 `@PreAuthorize`의 `AuthorizationDeniedException`을 삼켜 500+ERROR로그)임을 발견. `GlobalExceptionHandler`에 `AccessDeniedException→403 FORBIDDEN` 핸들러 추가(R43/R49 견고성 보완, 로그 미출력). 신규 스키마 없음. 단위 `accessDenied_maps_to_403` 추가·전체 그린, 라이브 TESTER 4개 admin 경로 403·admin 200·미인증 401·`Unhandled exception` 0건, smoke 0에러 |
| R79 | **Explain 피드백 `helpful` 필수화** (BE, 무결성) — 쓰기 엔드포인트 빈/불량 바디 스윕에서 `POST /explain/{id}/feedback`만 빈 바디 `{}`가 200 통과함을 발견. `Request.helpful`이 primitive boolean이라 누락 시 `false`로 채워져 "아쉬워요"가 조용히 기록·유용률(R22) 오염. `@NotNull Boolean`으로 변경 → 누락/null 400. 나머지 쓰기 경로는 R43/R58/R59로 이미 견고(전부 400/404). 단위 `ExplainFeedbackRequestTest` 2건 추가·그린, 라이브 `{}`·null→400·`true`→200, smoke 0에러 |
| R80 | **IMALOL C 예상가 거리 노출** (FE, R66/R67 확장) — 활성 신호의 큰 축인 IMALOL이 항상 `c_target`(=projectedClose 박스 투영가)을 갖는데 카드/상세의 C목표 거리(R66)가 ABC/TOP 전용이라 빠져 있던 것을 확장. 카드는 "C {가격} (±X%)", 상세는 라벨 구분(ABC/TOP="C 목표가", IMALOL="C 예상가", Explain 문구와 일치). R:R은 invalidation 있는 ABC/TOP 유지(IMALOL은 무효화가 null). mock 이미 패리티. FE 전용, typecheck/build/라이브 smoke 0에러, IMALOL 상세 c_target 렌더 확인 |
| R81 | **홈 주목신호 종목별 dedup** (BE, 큐레이션 품질) — 실화면 점검에서 `/signals/top`이 같은 종목을 타임프레임만 달리해(1w·3d 동일 IMALOL) 중복 노출함을 발견(6칸에 WBA 2번=유니크 5). `SignalService.top()`에 종목별 최상위 1건만 남기는 `dedupeByInstrument`(넉넉히 fetch 후 dedup) 추가. 정렬·필터·계약 무변경. 단위 3건 그린, 라이브 `top?limit=6`→6종목 유니크(빈 슬롯을 다른 시장·패턴 후보가 채움), smoke 0에러. (참고: top은 구조점수 정렬 — 종합 Pattern Score 정렬은 pattern_score 영속화 선행 필요, 백로그) |
| R82 | **시간 만료 신호 활성 후보 제외** (BE, read-model 정합성) — 활성상태 111건 중 58건이 `expires_at` 경과인데도 상태 미전이(스케줄러 지연/게이트)로 `top`·활성목록이 만료 신호를 최신 후보로 노출(홈이 3개월 지난 IMALOL을 score100 최상단에 띄움). `findTopByScore`·`findPage`(activeOnly 분기)에 `expiresAt>:now` 가드 추가(비파괴적 read 필터, 상태 전이는 여전히 스케줄러). 기본 목록(active_only=false) 하위호환. 라이브 top이 유효 후보(AMZN·CI·ABBV…)로 교체, smoke 0에러 |
| R83 | **신호 카드 관심 등록 토글** (FE, 마찰 감소) — 관심 등록이 신호 상세에서만 가능해 홈 주목신호·스캐너 목록에서 바로 못 담던 것을, `SignalCard`에 별 토글 추가(담김=채운 별). `useWatchlist`로 소속 판정, add/remove 훅으로 토글, `<Link>` 내부라 prevent/stopPropagation. 3곳(홈·스캐너·종목상세) 공유 일괄 적용, 워치리스트 쿼리 키 공유로 카드별 재요청 없음. mock 패리티 O. FE 전용, typecheck/build/smoke 0에러, 워치리스트 add/remove 왕복 실측 |
| R84 | **신호 상세 액션 시장별 현실화** (FE) — "다음 액션"이 주식에도 FUTURES+레버리지+롱/숏을 하드코딩하던 것을(비현실적 "AMZN 3배 숏"), `market==="CRYPTO"` 분기로 코인=선물 롱/숏+레버리지, 주식=현물 매수(SPOT BUY, 레버리지·숏 숨김+안내)로 수정. 백엔드 무변경. typecheck/build/smoke 0에러, 주식 상세 단일 현물매수 렌더 확인, `POST /paper/orders SPOT`(AMZN)→201 FILLED(leverage 1) 실측 후 리셋 원복 |
| R85 | **신호 상세 유효기간(만료 D-day) 노출** (BE+FE) — 활성 신호가 언제까지 유효한지 안 보이던 것을, `SignalDetailDto.expiresAt` 추가(detail()에서 채움)+상세 헤더에 "유효기간 {일시} (D-N)" 라인(임박 경고색, 만료시 "만료됨·갱신대기"). setup 잔여 유효기간을 진입 판단에 제공. mock도 expires_at 파리티(detected+30d). BE test 그린, 실측 `/signals/88`={expires 2026-09-14, D-2}, FE build/smoke 0에러 |
| R86 | **신호 상세에 "이 패턴 과거 성과(base rate)" 노출** (FE) — 성과 패널이 이 신호 자체 실현수익만 보여줘 갓 탐지된 신호(판단이 가장 필요한 순간)엔 "측정 대기"만 뜨던 것을, 성과 카드 최상단에 같은 유형(type·market·timeframe) 과거 `표본 N·1일 적중률·평균`을 `/signals/performance/summary`(스캐너 스트립과 동일 데이터) 재사용으로 노출(표본<20 "참고만" 배지). 해자(실측 적중률)를 개별 신호 판단 지점에서 바로 확인. 신규 API 없음, mock 패리티. typecheck/build/mock smoke 0에러, 상세(ABC·CRYPTO·4h) 표본42·61.9%·+3.20% 렌더 확인 |
| R87 | **API_CONTRACT.md 동기화** (문서) — R74(→R73) 이후 R77~R86 계약 델타 미반영분을 문서↔코드 정적 대조로 반영: **누락 엔드포인트 2개**(`/signals/{id}/performance`·`/signals/performance/summary` — 해자, FE가 쓰는데 계약서에 없었음) 추가, R77 무효화 완충(`invalidation{rule,price,buffer_pct,effective_price}`), R85 `expires_at`, R79 `helpful` 필수(400), R80 IMALOL c_target, R49/R78 에러 정규화(404/403). 코드 무변경 |
| R88 | **PROJECT_STATUS 통계 드리프트 교정** (문서) — 단일 진입 문서 §2.1이 코드 실측과 어긋나 있던 것을 실측으로 갱신: 컨트롤러 28→**33**, 엔드포인트 ~78→**~89**, 마이그레이션 V1~V24→**V1~V28**, FE 라우트 14→**16**·TS ~12,057→**~14,200줄**. 기준일 2026-09-08→2026-09-13. 코드 무변경(정적 카운트로 검증) |
| R89 | **홈 주목신호 카드 과거 적중률 칩** (FE, R86 확장) — 큐레이션 카드에서 열어보기 전에 pattern base-rate로 triage. `SignalCard`에 opt-in `perfHint`(표본≥10일 때만 "과거 적중 X%(nN)"), `top-signals`가 `/signals/performance/summary`를 1회 조회해 type\|market\|timeframe 매칭. 스캐너/종목상세 목록은 무변경(노이즈 방지). mock 패리티, typecheck/build/smoke 0에러, 홈 4카드 적중률 렌더·표본없는 카드 미표시 확인 |
| R142 | **주간 리포트 집계 헬퍼 단위 테스트** (BE, 회귀 보호) — `recoverHits`·`pct`·`signed`(전체 가중집계 빌딩블록) package-private+3케이스. ASCII 경로 그린, 로직 무변경 |
| R141 | **주간 리포트 하이라이트 선정 단위 테스트** (BE, 회귀 보호) — R34 해자 `highlights`(표본5+·BEST/WORST) package-private+3케이스. ASCII 경로 그린, 로직 무변경 |
| R140 | **라이브 가격 스토어 단위 테스트** (FE, 회귀 보호) — 프로덕션 WS `livePrices.setMany` 병합/필터 6케이스(무효행 스킵·병합·ts 폴백·no-op). vitest 47/47, tsc 통과 |
| R139 | **백테스트 과적합 경고 규칙 단위 테스트** (BE, 회귀 보호) — §11 `isOverfit`(승률 15%p↓·수익 반전·0거래) package-private+5케이스. ASCII 경로 그린, 로직 무변경 |
| R138 | **decimalString·ratioPct lib 추출 + 테스트** (FE) — 모의투자 로컬 수량/비율 헬퍼를 `lib/format`로 추출+테스트. vitest 41/41, tsc/build 통과 |
| R137 | **조건검색 빈도등급/매치율 단위 테스트** (BE, 회귀 보호) — R24 `matchRate`·`frequencyGrade` package-private+6케이스(가드·HALF_UP·등급 경계). ASCII 경로 그린, 로직 무변경 |
| R136 | **조건검색 비교 로직 추출 + 테스트** (BE, 회귀 보호) — 스캐너 핵심 연산자 비교를 `ConditionScannerService.compare`로 추출(순수)+5케이스(경계·null·미지원). ASCII 경로 그린, 동작 동일 |
| R135 | **R77 실질 무효화가 실측 예시 회귀 고정** (BE, 테스트) — `thresholdPrice(89.25,0.03)=86.5725`(/signals/275 실측) assertion 추가. ASCII 경로 그린, 소스 무변경 |
| R134 | **이벤트 리스크 D-day 라벨 단위 테스트** (BE, 회귀 보호) — R39 `ddayLabel`(D-DAY/D-N/D+N) package-private + 테스트. ASCII 경로 그린, 로직 무변경 |
| R133 | **경제 캘린더 순수 헬퍼 단위 테스트** (BE, 회귀 보호) — R39 `firstFriday`(NFP 첫째 금요일)·`dday`를 package-private로 열고 4케이스. ASCII 경로 그린, 로직 무변경 |
| R132 | **시장 국면 판정 로직 단위 테스트** (BE, 회귀 보호) — R37 `MacroService.label`(BULL/BEAR/RANGE/TRANSITION)을 package-private로 열고 6케이스(밴드·혼재·빈신호). ASCII 경로 그린, 로직 무변경 |
| R131 | **fmtNum lib 추출 + 테스트** (FE) — 홈 로컬 ko-KR 숫자 포맷터를 `lib/format`로 추출+6케이스. 숫자/통화 포맷 헬퍼 lib 중앙화 완료(R124·127·129·131). vitest 37/37, tsc/build 통과 |
| R130 | **홈 죽은 코드 제거** (FE, 정리) — 이름과 달리 ₩를 반환하며 호출부 없던 미사용 `compactUsd`(홈) 삭제(글로벌은 compactUsdScaled 담당). tsc/build/vitest 그린 |
| R129 | **compactUsdScaled lib 추출 + 테스트** (FE) — 홈 로컬 $조/억/만 포맷터를 `lib/format`로 추출+5케이스. vitest 35/35, tsc/build 통과 |
| R128 | **mock 성과요약 필터 단위 테스트** (FE) — 오프라인 데모용 `getSignalPerformanceSummary` 필터 5케이스. vitest 33/33, 소스 무변경 |
| R127 | **koreanMoney lib 추출 + 테스트** (FE) — 모의투자 로컬 억/만원 포맷터를 `lib/format`로 추출(재사용·테스트 가능)+6케이스. vitest 28/28, tsc/build 통과 |
| R126 | **toChartTime·formatTime 단위 테스트** (FE, 회귀 보호) — 남은 순수 헬퍼 테스트(26/26). FE format/types/api 커버리지 거의 완비. 소스 무변경 |
| R125 | **indicesByKey 단위 테스트** (FE, 회귀 보호) — 홈 시장지표용 순수 헬퍼에 테스트 3케이스(매핑·null→빈맵·중복 last-wins). vitest 24/24, 소스 무변경 |
| R124 | **compactUsd 단일 소스화 + 테스트** (FE, dedup·회귀) — 3개 페이지 중복 정의(data·derivatives·instruments)를 `lib/format.compactUsd`로 통합(홈 ₩/조억 버전은 별개라 유지), 테스트 3케이스. vitest 21/21, tsc/build 통과 |
| R123 | **FE 테스트 alias 설정 + extractError 커버리지** (FE, 회귀 보호) — `vitest.config.ts`에 `@`→루트 alias 추가(aliased 모듈 테스트 인프라), `lib/api.test.ts`(extractError 4케이스). vitest 18/18 그린, tsc/build 무영향 |
| R122 | **프론트 단위 테스트 도입(vitest)** (FE, 회귀 보호) — FE 14k줄에 단위 테스트가 0이던 것을, dev 전용 vitest@1.6 도입 + 순수 헬퍼 테스트(format 9·types 5, 14 그린). `npm run test`. tsc/build 무영향(dev 전용). |
| R121 | **라이브 견고성 프로브 — 잔여 500 없음** (BE, 검증) — 백엔드 라이브 기동 후 읽기/쓰기 불량입력 20종 스윕: 나쁜 enum/cursor/ISO/봉·없는 id·빈 바디 전부 4xx(또는 clamp성 200), **500 0건**. R43~R79 하드닝 유효 재확인. 코드 무변경 |
| R120 | **로드맵 §4 현 상태 반영** (문서) — R86~R119로 웹/BE 폴리시 소진, 여러 화면 점검 결과 이미 완료 확인. §4 서두에 "폴리시 소진, 남은 실질 항목은 보류된 Flutter/결제뿐" 요약 추가(향후 미세 재탕 방지). 코드 무변경 |
| R119 | **§2.3 데이터 규모 실측 교정** (문서) — 캔들 24만→29.6만(295,840)·신호 410→316(R90/R101 라이브 DB 실측). 종목수는 미재확인이라 유지. 코드 무변경 |
| R118 | **홈 관심종목 개수 표시 + R117 시각 검증** (FE) — 관심종목 헤더에 `(N)` 개수 표시. R117 레이아웃·R112 등락률을 mock smoke+홈 캡처로 확인(0에러). typecheck/build 통과 |
| R117 | **홈 관심종목을 시장개요 위로** (FE, 실사용 우선순위) — 개인 관심종목이 맨 아래(시장개요 다음)라 매일 확인에 스크롤 필요하던 것을 검색 바로 아래로 상향. typecheck/build 통과 |
| R116 | **전체 테스트 스위트 통합 그린 확인** (BE, 헬스체크) — R109~R115 신규 테스트 4종 포함 전체 `gradlew test` BUILD SUCCESSFUL(파일 22·@Test 98). 관리자 감사action·알림status 등 잔여 raw enum은 운영자용 기술코드라 한글화 안 함으로 결정. 코드 무변경 |
| R115 | **TimeUtil ISO·freshness 단위 테스트** (BE, 회귀 보호) — 전화면 최신/지연 배지 로직(`freshness`=경과>2×봉 시 지연)에 테스트가 없던 것을 `TimeUtilTest`(5케이스)로 보강: ISO 라운드트립·null·2×봉 경계(배타적). ASCII 경로 그린, 코드 무변경 |
| R114 | **관리자 분포 차트 상태 라벨 한글화** (FE, R113 후속) — 사용자상태·신호상태 분포의 raw enum을 한글화(`Distribution` labels prop). 신호상태는 badges `STATUS_LABEL` export 재사용(단일 소스). typecheck/build 통과 |
| R113 | **관리자 사용자 상태 배지 한글화** (FE, 용어 일관성) — raw APPROVED/LOCKED/PENDING → 승인됨/잠김/승인 대기(`USER_STATUS_LABEL`). mock smoke 0에러·캡처 확인 |
| R112 | **홈 관심종목 라이브 등락률** (FE, 실사용) — 관심종목이 현재가만 보이던 것을, 크립토 라이브 WS의 change_rate로 등락률(+X%, 색)을 가격 옆에 표시(BE 변경 없음). typecheck/build 통과 |
| R111 | **Timeframe 지원 매트릭스·코드파싱 단위 테스트** (BE, 회귀 보호) — (시장,봉) 유효조합 단일 소스에 테스트가 없던 것을 `TimeframeTest`(6케이스)로 보강: fromCode 대소문자·무효 예외·duration·주식 D1/D3/W1만·코인 전체. ASCII 경로 그린, 코드 무변경 |
| R110 | **CursorUtil 라운드트립·불량커서 단위 테스트** (BE, 회귀 보호) — 다수 목록이 쓰는 커서 페이지네이션에 테스트가 없던 것을 `CursorUtilTest`(5케이스)로 보강: 라운드트립 보존·URL-safe base64·불량 커서→INVALID_CURSOR(500 아님). ASCII 경로 그린, 코드 무변경 |
| R109 | **펀딩차익 기대수익 계산 단위 테스트** (BE, 회귀 보호) — R108에서 드러난 "expected_1x/2x=펀딩 징수 횟수" 의미를 `FundingServiceTest`(6케이스)로 고정. 1회=pct−0.21·2회=2pct−0.21·클램프·null. ASCII 경로 그린, 코드 무변경 |
| R108 | **펀딩차익 라벨 정정(R107 오류 복원)** (FE) — R107의 "1배/2배"(레버리지)는 오류. 백엔드 `expectedProfitPct=pct×count−fee`(count=펀딩 징수 횟수) 확인 후 **"펀딩 1회/2회"**로 복원·명확화. 교훈: 용어 변경 전 계산 의미를 코드로 확인 |
| R107 | ~~펀딩차익 "1회/2회"→"1배/2배"~~ **(R108에서 오류로 판명·복원)** — "1x/2x"는 레버리지가 아니라 펀딩 징수 횟수였음 |
| R106 | **조건검색 지표 라벨 다듬기 + 누적 스모크** (FE) — 조건검색 지표 드롭다운 "MA5"→"이동평균5(MA5)", "MACD Histogram"→"MACD 히스토그램". R102~R106 mock full smoke 0에러 |
| R105 | **종목 상세 MACD 부호 색** (FE, R104 후속) — 지표 요약 MACD를 양수 초록·음수 빨강으로 강조(RSI 색과 일관). typecheck/build 통과 |
| R104 | **종목 상세 RSI 과매수·과매도 색/라벨** (FE, 실사용) — 지표 요약 RSI가 숫자만이던 것을 ≥70 과매수(빨강)·≤30 과매도(초록) 색+라벨로 강조. typecheck/build 통과 |
| R103 | **mock top-signals 종합점수 정렬 파리티** (FE, R90 파리티) — mock `/signals/top`이 구조 score로 정렬해 백엔드(coalesce(pattern_score,score)) 순서와 달랐던 것을 동일 정렬로 수정. 오프라인 데모 홈 순서 일치. typecheck/build 통과 |
| R102 | **신호 상세 헤더 종합 점수 노출** (FE+문서, R101 후속) — 카드는 "종합 {점수}"인데 상세 헤더는 "구조 점수"만이던 불일치를, `HeaderScore`로 "종합 · 구조" 병기(explain 쿼리 공유, 추가 fetch 없음). +§2.2 Pattern Score 항목 갱신. typecheck/build 통과 |
| R101 | **종합 Pattern Score 카드 노출** (BE+FE, R90/R98 완결) — 홈이 종합 점수로 정렬하는데 카드엔 구조 점수만 보이던 것을, `SignalDto.patternScore` 추가+카드가 "종합 {점수}" 표기(미계산 시 구조 점수 폴백). 라이브 `/signals/top` pattern_score 노출·정렬 일치 확인, 전체 test 그린, mock smoke 0에러, 홈 카드 "종합 76.0…" 렌더 |
| R100 | **모의투자 주문 내역 방향 한글화** (FE, 용어 후속) — 주문 내역 BUY/SELL→매수/매도, LONG/SHORT→롱/숏(+청산), `orderSideLabel` 헬퍼. 폼 토글 버튼은 관례상 영문 유지. typecheck/build 통과 |
| R99 | **스캐너 필터 패턴 라벨 단일 소스화** (FE, 품질) — 필터바가 패턴 라벨을 카드 배지 `SIGNAL_TYPE_LABEL`와 별도 하드코딩(드리프트 위험, R70 유형)이던 것을 공유 상수 재사용으로 통합. 동작 동일, typecheck/build 통과 |
| R98 | **홈 주목신호 정렬 기준 명시** (FE, R90 후속 마찰) — 종합 점수로 정렬하는데 카드엔 구조 점수만 보여 순서가 뒤죽박죽처럼 보이던 것을, "종합 점수 순 · 완성도+거래량+추세+변동성+뉴스" 안내 추가. mock smoke 0에러 |
| R97 | **종목 상세 "최근 신호" 활성 우선 정렬** (FE, 실사용 마찰) — 상태 무관 최신순이라 실패·만료가 활성 신호를 가리던 것을, 활성(탐지/완성임박) 우선 안정 정렬(이력은 아래로 보존). typecheck/build 통과 |
| R96 | **용어 2차 다듬기(모의투자)** (FE, R93 후속) — 포지션 ROE→"수익률(ROE)", 주문 내역 raw SPOT/FUTURES→"현물"/"선물"(포지션 목록과 일치). typecheck/build 통과 |
| R95 | **용어 2차 다듬기** (FE, R93 후속) — 신호 상태 배지 INVALIDATED "무효"→"실패"(본문 "실패" 용어와 통일), 차트 오버레이 토글 "MA"→"이동평균". typecheck/build 통과, 로직 무변경 |
| R94 | **API_CONTRACT 잔여 미문서 엔드포인트 정리** (문서, R92 감사 후속) — market 5개(indices/{key}/history·fear-greed/history·movers·trending·global) + 파생 신설 §6-1(derivatives·liquidations) + 알림 웹푸시 2개(config·subscription)를 컨트롤러 정적 대조로 추가. 주요 엔드포인트 계약 반영 완료. 코드 무변경 |
| R93 | **용어 사용자친화화** (FE, 사용자 실사용 피드백) — "용어가 알아듣기 어렵다"는 피드백에 따라 전 화면 전문용어를 "쉬운말 + (원래 용어) 병기" 스타일로 정리: MFE/MAE→최고상승/최대하락, 무효화→실패 기준, R:R→기대수익÷손실, Risk Guard→위험 차단, 워크포워드/과적합/IS·OOS·MDD·Profit Factor, 스캘핑 지표(호가차·초당체결·쏠림), 도미넌스→점유율, TVL→예치금, FDV→총가치, OI 병기, 쿨다운→재알림 간격, 적중률→성공률·표본→사례. 패턴명 등 고유어는 유지. 백엔드/로직 무변경, typecheck/build/smoke 0에러 |
| R92 | **API_CONTRACT에 백테스트·전략·모의투자 섹션 추가** (문서) — R91 중 `/paper`·`/backtests`·`/strategies`가 단일 진실 소스에 통째로 누락됨을 발견, 컨트롤러·DTO 정적 대조로 "4-1. 검증 도구" 신설(요청/응답 필드·walk_forward·OrderResponse symbol 등). 코드 무변경 |
| R91 | **모의투자 주문 내역 종목 심볼 노출** (BE+FE) — 내역 탭이 종목을 `#{instrument_id}`(숫자)로 보여 "BUY #1"처럼 안 읽히던 것을(포지션 탭은 이미 symbol 있음), `OrderResponse`에 `symbol`·`name` 추가(포트폴리오 recent_orders는 배치 로드로 N+1 회피)+FE는 `symbol ?? #id`. 라이브(BTC SPOT 주문→응답·portfolio 모두 KRW-BTC)+전체 test 그린+mock smoke 0에러, 내역 "BUY KRW-BTC" 렌더 확인 |
| R90 | **홈 주목신호 종합 Pattern Score 정렬** (BE, R81 백로그 해소) — `/signals/top`이 구조점수(완성도)로만 정렬해 "구조만 예쁘고 근거 빈약한" 신호가 상단이던 것을(실측: 구조92.2·종합46 신호가 top), 완성도+거래량+추세+변동성+뉴스 **종합 Pattern Score**로 정렬. V29 `pattern_signals.pattern_score`+부분인덱스, `SignalExplainService.computeScore`(explain과 동일 경로 공유), `SignalPatternScoreService`+스케줄러(기동 백필+15분 주기, ingestion 독립), `findTopByScore` → `coalesce(pattern_score,score)`. 라이브 검증: V29 적용, 활성111 백필, 종합순 top 반환(구조92.2·종합46 신호 탈락), 전체 테스트 그린. API/DTO 무변경 |

---

## 3. 지금 상태의 진실 — 검증된 것과 아닌 것

솔직하게 구분해둔다. 이걸 흐리면 나중에 내가 속는다.

| 항목 | 실제 상태 |
|---|---|
| **백엔드** | 실데이터로 가동. compileJava/compileTestJava 성공, 단위·골든 테스트 통과 |
| **프론트** | build + typecheck 성공, 전 라우트 라이브 Chrome smoke **0에러** |
| **Flutter 앱** | **`flutter analyze` 한 번도 안 돌아감.** `android/`·`ios/` 폴더 없음(`flutter create .` 필요). 모의투자·백테스트·조건검색·Admin·파생·청산·Explain 피드백 **전부 미연동** |
| **주식 데이터** | 사이드카 켜져 있으면 실데이터, 꺼져 있으면 **합성 스텁으로 조용히 폴백**. 스텁인지 구분하려면 `/system/status` 확인 |
| **텔레그램 속보** | 사이드카 경유. 사이드카 없으면 Bloomberg RSS만 |
| **틱띄기** | **WS 실시간 가능(R38).** `vein.scalp.ws-enabled=true`면 Upbit WebSocket 수집기(orderbook+trade 연속 스트림)가 @Primary. 기본은 REST 폴링 근사(폴백) |
| **거시경제/시장국면** | **구현됨(R37).** `com.vein.macro` — 국면은 내부 데이터(나스닥 추세+공포탐욕)로 항상 판정. 금리차·M2·DXY는 **FRED 키 설정 시** 채워짐(현재 키 미설정이라 해당 블록 null) |

**실연동(키 불필요)**: Upbit · Binance · CoinGecko · alternative.me · DefiLlama · Bybit · Bloomberg RSS
**스텁/근사(교체 TODO)**: 미국·국내주식(사이드카) · 텔레그램(MTProto) · 틱띄기(폴링 근사)

---

## 4. 뭘 더 해야 하나

> **현 상태(2026-09-13, R119 기준):** Track A #1~#4 완료, Track B #2~#4 완료. R86~R119에서
> 실사용 마찰 제거·용어 사용자친화화(쉬운말+용어 병기)·종합 Pattern Score 정렬/노출·백엔드
> 순수함수 테스트 커버리지 보강까지 **웹/백엔드 폴리시는 사실상 소진**. 남은 실질 항목은
> 아래 **Track B #1(Flutter, 사용자 지시로 보류)**과 **Track C(결제 PG·상용화, 착수 보류)**
> 뿐 — 즉 큰 결정/인프라가 필요한 것들이다. 자잘한 미세 폴리시를 재탕하기보다, 다음 실질
> 진전은 이 보류 항목 중 하나를 사용자가 열어줄 때 시작된다.

우선순위를 **"누구를 위한 것인가"** 로 3트랙으로 나눈다. 위에서부터 한다.

### Track A — 내가 쓰기 위해 (최우선)

이게 끝나야 "데스크톱 터미널을 안 켜도 된다"가 성립한다.

1. ✅ **거시경제 / 시장국면 엔진** (R37+R39 구현 완료)
   - 장단기 금리차(10Y-2Y, 10Y-3M) · M2 · DXY — **FRED 키 설정 시** 활성
   - **BULL / BEAR / RANGE / TRANSITION** 국면 판정 — 내부 데이터로 가동 중
   - ✅ 경제 캘린더(FOMC·CPI·고용, keyless) + 종목별 실적발표 D-7~D-day 라벨 (R39, `/macro/calendar` + 사이드카 yfinance)
   - ✅ 이벤트 전후 `EVENT_RISK` 라벨로 신호 confidence 조정 (R39, 신호 상세 `event_risk` 읽기 시점 힌트)
   - → *왜 1순위인가: 개별 신호를 볼 때마다 "지금 시장이 어떤 국면인지"를 매번 따로 확인하고 있다. 그게 자동화되면 판단 시간이 줄어든다.*
   - *남은 보강: 경제 캘린더 고정일은 큐레이션 상수(연 1회 갱신) → 실 캘린더 API 연동, FRED 키 발급.*
2. ✅ **사이드카를 별도 저장소로 분리** — SIDECAR_VEIN 저장소 + `requirements.txt`로 standalone 실행 가능(R40에서 README를 독립 venv 우선으로 마감). 남은 것: 배포 자동화는 아직 수동
3. **실사용 마찰 제거** — 실제로 며칠 써보면서 걸리는 것부터 (알림 노이즈? 신호 과다? 차트 조작감?). ✅ 스텁 폴백 가시화(R40), ✅ 신호 과다 → 홈 "오늘의 주목 신호" 상위 큐레이션(R41), ✅ 알림 노이즈 → 조용한 시간(R42), ✅ 알림 요약 → 다이제스트(R45), ✅ 알림 스풀링 → 조용한 시간 보류 후 방출(R46), ✅ 차트 조작감 → 차트 마운트-1회 재사용 + 크로스헤어 레전드 + 터치 튜닝(R47). **Track A #3 마감**
4. ✅ **틱띄기 실시간화** (R38) — Upbit WS 수집기 구현. `vein.scalp.ws-enabled=true`로 활성(라이브 검증됨), 기본은 REST 폴백

### Track B — 남에게 보여주려면

혼자 쓸 때는 없어도 되지만, 두 번째 사용자가 생기는 순간 필수가 되는 것들.

1. **Flutter 앱 따라잡기** — 또는 **명시적으로 접기**. 지금처럼 절반만 살아있는 게 제일 나쁘다
   - 기획서 2.1도 "Next.js 우선, Flutter는 검증 후 전환"이라 접는 것도 정합적인 선택
2. ✅ **온보딩** — 홈 "시작하기" 체크리스트(관심종목·알림·모의투자·조건검색, 실제 상태 파생)로 신규 유저를 핵심 기능에 안내(R48). 남은 것: 가입 직후 웰컴/투어 심화는 후속
3. ✅ **데이터 출처·지연 표시 강화** — R40(설정 배지)에 이어 R50에서 **사용 지점**으로 확장: 전역 합성값 경고 배너 + 종목 상세 "합성" 배지. 스텁 폴백을 데이터 보는 자리에서 바로 인지
4. **엣지케이스** — 나 혼자 쓰는 경로만 밟고 있어서 잠재된 것들. ✅ 잘못된 요청 4xx(R43), ✅ 미매핑 경로 404(R49, 신규유저 실측으로 발견). 인접 엣지(path var·cursor·window 클램프)는 실측상 이미 견고

### Track C — 상용화하려면

**지금 손대지 않는다.** 기획서에는 있지만 Track A/B가 끝나기 전엔 착수 대상이 아니다.

- 구독·결제 · 무료/PRO 기능 제한표 · 사용량 정책
- AI Trading Assistant(§13) · Replay Engine(§14) · Strategy Marketplace(§12)
- Execution Realism(§37) · Portfolio Risk(§38) · Data Lineage(§44)
- 웹훅 계약(§74) · 전략 SDK(§75) · 파트너 거버넌스(§78)
- 약관·개인정보·투자 고지 정식화 · 외부 데이터 재배포 권리 확인

### 영구 보류 (컴플라이언스)

- 실거래소 주문 · 실자금 연동 · 자동매매 · 정확한 선물 청산가 계산

---

## 5. 고객층

### 5.1 동심원

```
┌──────────────────────────────────────────────┐
│  ③ PRO 전환 가능층 — 아직 가설               │
│  ┌────────────────────────────────────────┐  │
│  │  ② 나와 같은 문제를 가진 사람          │  │
│  │  ┌──────────────────────────────────┐  │  │
│  │  │  ① 나 — 유일하게 확정된 사용자   │  │  │
│  │  └──────────────────────────────────┘  │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

**① 나 (0순위, 유일하게 검증된 수요)**

- 코인·미국주식·한국주식을 같이 보는 개인 트레이더
- 이미 기술적 패턴(ABC/고점/이말올) 기반으로 매매 중 — 즉 **이 도구의 로직을 이미 신뢰하고 있다**
- 데스크톱 터미널을 직접 만들어 쓸 정도로 기존 서비스에 만족 못 함
- **요구**: 자리에 없어도 후보를 놓치지 않을 것, 신호에 근거가 붙을 것, 성과가 쌓일 것

**② 나와 같은 문제를 가진 사람 (1순위 확장, 미검증)**

- 여러 시장을 동시에 보는데 **시장마다 도구가 따로 노는** 개인투자자
- TradingView 알림으로는 부족하고, 직접 스크립트 짜기는 부담스러운 층
- 패턴 매매를 하지만 **하루 종일 차트를 볼 수는 없는** 직장인 트레이더
- → *이 층이 실재하는지는 아직 데이터가 없다. 내가 쓰다가 "이거 남 줘도 되겠다" 싶은 시점이 검증 시작점.*

**③ PRO 전환 가능층 (가설 단계)**

- 기획서 기준: 무료(시세·차트·기본검색) → PRO(고급 조건검색·패턴탐지·실시간 알림)
- **아직 검증할 데이터가 없다.** ②가 실재한다는 근거가 나오기 전에 결제를 붙이는 건 순서가 틀렸다

### 5.2 경쟁 포지셔닝 (기획서 v1.2)

| 기존 서비스 | 그쪽 성격 | VEIN의 방향 | 차별화 |
|---|---|---|---|
| Bloomberg | 기관/전문가 중심 | 개인투자자용 모바일 터미널 | 복잡도 축소, 접근성 |
| TradingView | 차트/스크립트 중심 | 차트 + 조건검색 + 패턴 탐지 | **실행 가능한 후보 추천** |
| Finviz | 스크리너 중심 | 모바일 조건검색 | **한국·미국·코인 통합** |
| Stocktwits | 커뮤니티 중심 | 데이터 기반 설명 | 노이즈 제어, 신호 중심 |

**실질적 차별점 한 줄**: *"한국·미국·코인을 한 화면에서, 패턴 후보를 근거와 함께, 모바일로."*
이 조합을 하는 서비스가 없다는 게 애초에 직접 만들게 된 이유다.

### 5.3 수익 모델

**목표: 부수입 수준(월 100~500만 원). 단, 지금 착수할 일은 아니다.**

순서는 이렇다:

```
내가 만족스럽게 쓴다  →  무료 공개 + 후원으로 지불의사 측정  →  근거가 나오면 PRO
                                                                    ↓
                                                        그때 비로소 규제 비용을 지불한다
```

전략은 **콘텐츠 우선 → 구독**이다. 1인 개발자에게 진짜 제약은 자본이 아니라
**유입을 만들 시간**이라, BM 선택 기준은 하나로 좁혀진다 —
**"제품을 만드는 행위 자체가 마케팅이 되는가?"**

이 제품의 해자는 스캐너가 아니라 **"이 패턴이 실제로 먹히는가"에 대한 실측 데이터**
(신호 성과 추적)다. 그걸 **주간 리포트로 먼저 현금화**하고, 그 콘텐츠가 구독의 유입을
만든다. 유료화 단계에서는 **"조언(패턴 신호)은 무료로 열고, 자동화·검증 도구에서 돈을
받는다"** — 법적 안전지대와 상업적 논리가 같은 방향을 가리킨다.

- 상세(BM 후보 평가 · 단계별 실행 · 코드 변경 · 티어표 · V23 스키마 · 후원 모델 ·
  법적 체크리스트)는 **[`MONETIZATION.md`](MONETIZATION.md)** 참조
- 관리자(운영자) 계정은 `UNLIMITED` 플랜으로 **모든 한도를 우회**한다

기획서의 KPI(DAU/MAU · PRO 전환율 · 구독 유지율)는 ③단계 지표다. 지금 추적해야 할 유일한
지표는 **"내가 데스크톱 터미널을 며칠째 안 켰는가"** 다.

---

## 6. 알려진 제약 / 함정

### 운영

- ⚠️ **사이드카 의존**: 미국·한국 주식, 텔레그램 속보, 틱띄기는 Python 사이드카 경유.
  사이드카가 죽으면 **에러 없이 합성 스텁으로 폴백**한다 → 가짜 데이터를 진짜로 착각할 수 있음.
  **R40부터 `/system/status`가 provider별 `source`(REAL/STUB) + `sidecar.healthy`를 반환**하고,
  설정 화면 "데이터 출처"에 실데이터/합성 배지 + 스텁 경고 배너를 띄운다 → 스텁 여부를 눈으로 확인 가능.
- ⚠️ **DB 비밀번호**: R33에서 회전됨. 기존 postgres 볼륨은 옛 비밀번호이므로
  `ALTER USER vein PASSWORD '<새 값>'` 로 맞춰야 기동됨
  (`docker compose down -v`는 캔들 24만 건 소실).
- ⚠️ **시드 계정 비밀번호**: V22로 회전됨. 새 값은 `backend/.env`의 `SEED_ADMIN_PASSWORD`에만 존재.
- ⚠️ **프론트 운영**: OneDrive `.next` 동기화 손상 이슈로 **프로덕션 빌드로 운영**.
  코드 변경 시 재빌드·재시작 필요(핫리로드 X). 손상 시 `.next` 삭제 후 재시작.

### 개발

- ⚠️ **한글+공백 경로**에서 `gradlew test` 실패(Gradle 워커 @argfile 인코딩 버그).
  → **현재 `C:\VEIN`은 ASCII 경로라 해소됨.**
- ⚠️ **Java 필드 네이밍**: 숫자 앞에 snake `_`가 안 붙는다(`change1d`, `trade_value24h`).
  신규 필드는 **백엔드 실제 출력을 확인한 뒤** 프론트 타입을 작성할 것.
- ⚠️ **ID 프리픽스**: 신호는 `sig_`/`ins_` 프리픽스로 반환되지만 상세/캔들 API는 숫자 id만 받는다
  (프론트 `stripIdPrefix` 적용됨).
- ⚠️ **Flyway V5 주석**: 옛 시드 비밀번호가 주석에 남아 있으나 V22로 무효화된 **죽은 참조**.
  V5를 수정하면 checksum이 깨져 기동 실패하므로 손대지 말 것.
- pykrx KRX 인덱스 구성종목 API가 자주 다운 → 코스피/코스닥 구성종목은 프리셋/캐시 폴백.
- ✅ **(해소) `AbcDetectorTest` 선행 실패** — R44에서 ABC 탐지기 버그로 확정·수정(0→A 레그
  바닥 가드 추가). ASCII 경로 전체 테스트 그린. 상세 `ROUND44.md`.

---

## 7. 문서 지도

| 문서 | 역할 |
|---|---|
| **`PROJECT_STATUS.md`** (이 문서) | 왜 만드는가 · 어디까지 왔나 · 뭘 할 건가 — **여기부터 읽는다** |
| `API_CONTRACT.md` | BE/FE/APP 공통 API 계약 — **단일 진실 소스** |
| `WORKLOG.md` | 라운드별 작업 이력 (최신이 위) |
| `ROUND*.md` | 개별 라운드 상세 |
| `LEGACY_FEATURE_MAP.md` | 레거시 데스크톱 11탭 → 모바일 IA 매핑 |
| `기획서_v1.2_추출.txt` | 79섹션 상용 SaaS 기획 — **참고 자료. 할 일 목록이 아니다** |
| `MONETIZATION.md` | 수익화 설계 — 티어·가격·게이팅·법적 체크리스트 |
| `MVP실행기준표_추출.txt` | 내부 알파 범위·완료 기준 |
| `../README.md` | 백엔드 실행법 · 환경변수 |

---

*이 문서는 현황 스냅샷이다. 상태가 바뀌면 이 문서를 먼저 고친다.*
