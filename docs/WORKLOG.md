# VEIN 작업 로그 (WORKLOG)

> 자율 개선 세션 기록. 최신 항목이 위. 시간은 KST.

---
## ▶ 진행 현황 & 재개 가이드 (2026-06-14 기준, Round 23까지 완료)

**한 줄 요약:** VEIN 모노레포(backend Spring Boot / frontend Next.js / app Flutter / sidecar Python)로 레거시 11탭을 모바일 5탭(홈·스캐너·속보·데이터·설정)에 이식. 4시장(코인·미국·코스피·코스닥) 실데이터로 가동 중. 기획서 핵심+후속(백테스트·전략·신호성과)까지 구현. **모두 라이브 Chrome smoke 0에러로 검증됨.**

**완료 기능 체크리스트**
- [x] 인증/관심종목/알림(쿨다운)·앱내알림·웹 알림(앱 실행 중)
- [x] 4시장 시세·캔들·차트(MA/볼린저 오버레이)·지표(RSI/MA/볼린저/MACD)
- [x] 패턴 **3종** ABC·고점판독(TOP)·이말올(IMALOL) + 상태전이(DETECTED/NEAR_COMPLETION/INVALIDATED/EXPIRED) — ※**삼각수렴(TRIANGLE)은 사용자가 의도적으로 제거**(V13/V14, 코드+DB+프론트 일관)
- [x] 신호 성과(1h~7d 수익률·MFE/MAE) + 패턴별 적중률 요약
- [x] Pattern Score v1(5요소 100점) + 규칙 기반 Explain·Risk Guard·표본 Confidence
- [x] Explain 유용함/아쉬움 피드백 + 사용자별 중복 방지·유용률 집계
- [x] 조건검색기 v1(RSI·거래량·MA·MACD, AND/OR, 즉시 실행·저장식)
- [x] 백테스트 엔진(+워크포워드 IS/OOS 과적합 경고) + 전략 저장/재실행/성과 히스토리
- [x] 시장지표(공포탐욕+30일 스파크라인·도미넌스·글로벌시총) · 김치프리미엄 · 급등락(4시장) · 트렌딩
- [x] 파생(Binance 선물 펀딩·OI·롱숏) · 펀비차익(Bybit) · TVL(DefiLlama) · 유통량(CoinGecko) · 테마/섹터 · 틱띄기 스캘핑
- [x] 속보(Bloomberg RSS 실연동 + 텔레그램 coinness) + 감성 배지 + 종목 태깅·딥링크
- [x] 실시간 가격 WebSocket(STOMP) 푸시 · 전체 Upbit KRW 유니버스(~265) · 이름 우선 표시(코인 한글/미국 영문) · 종목상세 통합패널(파생+관련속보)
- [x] Binance USD-M 전시장 강제청산 WebSocket · 최근 500건 순환 버퍼 · REST/STOMP 피드 · 청산금액 필터/롱숏 합계 UI

**재개 시 먼저 할 일**
1. 서비스 3개 띄우기(아래 "실행 중인 서비스"). 순서: docker(PG/Redis) → 사이드카 → 백엔드 → 프론트.
2. `WORKLOG.md`의 라운드별 이력 + 아래 "알려진 제약" 숙지.
3. 회귀 검증: `cd frontend && node scripts/smoke.mjs` (로그인→전 라우트 콘솔/페이지 에러, 0이어야 정상).
4. DB 마이그레이션 현재 **V18**까지(+V13/V14 = TRIANGLE 제거). 신규 마이그레이션은 V19부터.

**다음 작업(백로그, 우선순위)**
1. 조건검색 저장식 알림 평가·예상 발생 빈도 시뮬레이션.
2. 청산 스트림 1h/24h 집계·이상치 알림(현재는 최근 500건 메모리 피드).
3. 기본 Admin 대시보드(회원·알림·Explain 품질·시스템).
4. VAPID/FCM 기반 완전 백그라운드 푸시(현재 웹 알림은 앱 실행 중 폴링 기반).

**핵심 함정(반드시 인지)**
- 레포가 한글+공백 경로 → `gradlew test`는 워커 @argfile 인코딩버그로 실패(컴파일·bootRun 정상). 테스트는 ASCII 경로 복사본에서만.
- 프론트는 **프로덕션 빌드(`npm run build && npm run start`)로 운영** — dev `.next`가 OneDrive 동기화로 손상돼 404/500 유발. 코드 변경 시 **반드시 재빌드+재시작**(핫리로드 X). 손상 시 `rm -rf .next` 후 재시작.
- Java 필드의 숫자 앞엔 snake `_`가 안 붙음(`change1d`/`trade_value24h`/`expected1x_pct`/`market_cap_change24h_pct`). **신규 필드는 백엔드 실제 출력 확인 후 프론트 타입 작성.**
- curl로 한글 본문 전송 시 Git Bash 인코딩 깨짐(앱은 정상 UTF-8). 한글 검증은 앱/스크린샷으로.
- 신호 id는 `sig_`/`ins_` 프리픽스 반환하나 상세/캔들 API는 숫자 id만 허용(프론트 `stripIdPrefix` 적용됨).
---

## 실행 중인 서비스 (로컬)
- **사이드카** `:8099` — `C:\veni_invest_abc\.venv\Scripts\python.exe sidecar/app.py` (yfinance·pykrx·telethon·Upbit WS 스캘핑)
- **백엔드** `:8080` — `JAVA_HOME=<JDK21> INGESTION_ENABLED=true SCALP_ENABLED=true SIDECAR_URL=http://localhost:8099 ./gradlew bootRun` (JDK21 = Android Studio jbr `C:\Program Files\Android\Android Studio\jbr`)
- **프론트** `:3000` — **프로덕션**: `cd frontend && (.env.local: NEXT_PUBLIC_USE_MOCK=false) npm run build && npm run start` (실데이터). mock 보려면 USE_MOCK=true.
- 로그인(실): `tester@vein.local` / 비밀번호는 로컬 `backend/.env` 참조 (평문 기록 금지)
- DB·Redis: `docker compose up -d` (backend/)

## 알려진 제약/주의
- 레포가 한글+공백 경로(`바탕 화면\베인`) → `gradlew test`는 워커 @argfile 인코딩버그로 실패(컴파일·bootRun은 정상). 테스트는 ASCII 경로 복사본에서 통과(16/16).
- 미국/한국 주식·텔레그램·스캘핑은 Python 사이드카 경유. 사이드카 다운 시 백엔드가 합성 스텁으로 폴백.
- pykrx KRX 인덱스 구성종목 API가 자주 다운 → KOSPI/KOSDAQ 구성종목은 프리셋/캐시 폴백(가격·캔들은 정상).

---

## 작업 이력

### 2026-06-13 (자율 세션 시작)
- **프론트/Flutter ↔ 실백엔드 응답 구조 전수 정렬.** 목업 기준으로 만들어 어긋났던 부분 수정:
  indices(배열 keyed by key), kimchi, signals(nested instrument/pivots), tvl(change1d/7d·chains 콤마문자열), supply(coingecko_id/total_supply/max_supply), themes(counts), funding(expected1x_pct/2x), scalp, watchlist(.items), notifications(meta.unread_count), 지표(rsi14/boll_middle/macd_histogram, scalar snapshot). **ID 프리픽스 함정**: signals는 `sig_`/`ins_` 프리픽스 id 반환하나 상세/캔들 API는 숫자 id만 허용(`/signals/sig_169`→500) → 프론트/Flutter에서 strip 처리. 프론트 build+typecheck 통과, 라이브 스모크 전 라우트 200.
- **실데이터 연결 + 런타임 통합버그 수정**: Instant 쿼리 캐스팅(candle/signal/news/notification), TVL numeric 오버플로(V9), Upbit ticker 상폐심볼 필터, 테마 중복키 멱등화, 기동 백필 러너, CORS.
- **Python 사이드카 신설**: 미국(yfinance)·한국(pykrx) 주식 캔들/지수, 텔레그램(coinness, 기존 세션 재사용), 실시간 Upbit WS 스캘핑. 백엔드가 EquitySidecarClient/NewsSidecarClient/ScalpSidecarClient로 호출(폴백 포함).
- **종목 유니버스 동기화**: 사이드카 /equity/instruments → instruments 업서트(미국 171종 전체).
- **레거시 11탭 → 모바일 5탭 이식**(홈·스캐너·속보·데이터·설정), 4시장(코인/미국/코스피/코스닥), 패턴 4종(ABC/고점/이말올/삼각수렴), 백엔드 V1~V9.

### 검증 현황
- 백엔드 16/16 단위·골든 테스트(ASCII 경로), 전 API 200, 4시장 실데이터·실신호.
- 자율 검증기(puppeteer-core + 설치된 Chrome) 구축 중 → 로그인 후 각 화면 콘솔에러/스크린샷 수집.

### Round 1 — 안정화/실데이터 정합 (완료, Chrome 검증 0에러)
- **Chrome 자동 검증기 구축**: `frontend/scripts/smoke.mjs` (puppeteer-core + 설치된 Chrome) — 로그인→전 라우트 방문→콘솔/페이지 에러·401·스크린샷 수집. `node scripts/smoke.mjs`.
- 홈 크래시(`idx.fear_greed.value`) = indices가 배열인데 객체로 읽음 → 배열 keyed-by-key 매핑.
- **AppShell 하이드레이션 불일치**(서버 authed=false vs 클라 localStorage authed=true) → `useMounted` 게이팅.
- **설정 크래시**(`alert.instrument.symbol`) + **POST /alerts 500**(`market` 미지 필드) → 백엔드: V10(alerts에 timeframe·market 컬럼), AlertDto에 symbol/timeframe/market 보강, CreateAlertRequest에 market, **Jackson `fail-on-unknown-properties:false`**(미지 필드 500 부류 차단). 프론트: Alert 타입 평면화(alert_id/instrument_id/symbol).
- **401 노이즈 제거**: `bootstrapAuth()`(부팅 시 refresh 선제 교환) + Providers 렌더 게이팅 + `useUnreadCount` 인증 enabled 가드.
- favicon 추가(app/icon.svg). → **smoke 0 에러 (login/home/scanner/news/data/settings)**.

### Round 2 — 무키 공개 API 가치 추가 (완료, 라이브 smoke 0에러)
- **공포·탐욕 30일 히스토리**(alternative.me `?limit=N`) → 홈에 인라인 SVG 스파크라인.
- **파생지표**(Binance USD-M 선물, 무키): `GET /derivatives`(미결제약정·OI가치$·롱숏비율·펀딩·마크가) + `/derivatives/{symbol}`(롱숏/OI 히스토리) → 데이터탭 "파생" 서브탭 + 상세 라우트. 검증: BTC OI $6.27B·롱숏 1.58·펀딩 실값.
- **급등락 movers**(레거시 터미널 기능, Upbit /v1/ticker): `GET /market/movers?type=GAINERS|LOSERS|VOLUME` → 홈 급등/급락/거래량 탭. 검증: KRW-VVV +17.6% 등 실시간.
- 필드 드리프트 정렬: movers `trade_value24h`(Java 네이밍). Binance futures resilience4j 인스턴스 추가.
- 검증: 백엔드 compileJava OK, 프론트 build+typecheck OK, **라이브 Chrome smoke 0에러**, 홈 스크린샷 육안 확인.

### Round 3 — 신호 성과 추적 §31 (완료, 라이브 smoke 0에러)
- **V11 signal_performance** 테이블 + 스케줄러(10분, 기동 시 1회): 탐지가(current_price) 기준 1h/4h/1d/3d/7d 시점 수익률·MFE·MAE를 종목 캔들에서 계산(미래데이터 누수 없음, horizon 미경과 시 skip).
- `GET /signals/{id}/performance`(horizon별 수익률, fresh면 빈배열) + `GET /signals/performance/summary?type=&market=&timeframe=&horizon=`(패턴별 표본수·적중률·평균/중앙 수익률·MFE/MAE).
- 프론트: 신호상세 "성과" 패널 + 스캐너 "패턴 성과 요약" 스트립.
- 검증: 요약 실집계(TRIANGLE/15m 표본29·적중100%·평균+0.73%), signal_performance 53행. compileJava+build+typecheck+라이브 smoke 0에러.

### 최종 검증 (3라운드 후)
- 전 엔드포인트 26개 **200**. Chrome 라이브 smoke 전 라우트 **0 에러**(크래시/하이드레이션/401 전무).
- 서비스 가동: 사이드카:8099 · 백엔드:8080 · 프론트:3000.
- 데이터: 캔들 202,510 · 신호 409(US243·CRYPTO72·KOSDAQ56·KOSPI38) · 성과 53 · 종목 231 · 뉴스 63 · TVL 4,727.

### Round 4 — 실시간 가격 WebSocket 푸시 (완료, 검증)
- 백엔드: `spring-boot-starter-websocket`, STOMP `/ws`(SockJS, 공개 핸드셰이크), `PriceBroadcaster`가 2s마다 Upbit 전 KRW 티커를 `/topic/prices`로 브로드캐스트(gated `vein.ingestion.enabled`).
- 프론트: `@stomp/stompjs`+`sockjs-client`, `store/livePrices.ts`, `<LivePrices/>` 전역 마운트, 김프·급등락·종목헤더에 라이브가 오버레이(폴백 유지), 홈에 **● LIVE** 배지. opt-in `NEXT_PUBLIC_LIVE_WS=true`(.env.local에 활성화함).
- 검증: node STOMP 클라가 CRYPTO 263종 라이브 수신, 홈 ● LIVE + 급등락 실시간 갱신, smoke 0에러.

### Round 5 — 신호 상태 전이(NEAR_COMPLETION) + 부팅 순서 개선 (완료, 검증)
- `SignalStatusTransitionService`에 DETECTED→NEAR_COMPLETION 추가: ABC/TOP의 **최신 종가**가 C예상가 5% 이내(레거시 기준)면 전이. EXPIRED(TTL)·INVALIDATED(저점이탈)는 기존.
- `StartupIngestionRunner` 재정렬: 크립토 poll→scan→**조기 전이**→(느린)주식 sync/백필→주식 scan→전이→성과. 부팅 반응성↑, 전이가 부팅 시 즉시 반영.
- 검증: 상태 분포 DETECTED 155·EXPIRED 204·INVALIDATED 46·**NEAR_COMPLETION 5**(예: 034730.KS TOP/1w C 2.39% 근접). 4상태 전부 표현. smoke 0에러.

### Round 6 — 급등락 한글 종목명 (완료, 검증)
- `MoversService.nameBySymbol()`가 Upbit `/v1/market/all` 한글명(전 ~200 KRW 마켓)으로 폴백 + instrument 테이블명 오버레이. 검증: KRW-VVV 베니스토큰·KRW-OPEN 오픈렛저·KRW-MEGA 메가이더·KRW-TAO 비트텐서.

### Round 7 — 전체 코인 유니버스 + 이름 우선 표시 (사용자 요청, 완료·검증)
- **버그**: 에테나(KRW-ENA) 등 검색 안 됨 — 크립토 instruments가 V5 시드 20개뿐(미국주식만 사이드카 동기화했었음). → **`CryptoInstrumentSyncService`** 신설: Upbit 전 KRW 마켓(~263) instruments+provider_symbols(UPBIT/BINANCE) 업서트 + 기존 코인 이름을 Upbit 한글명으로 `rename`. StartupIngestionRunner 최우선 단계로 배선. 결과: CRYPTO 265종, **KRW-ENA "에테나" 검색·표시됨**.
- **이름 우선 표시**: 신호 DTO instrument에 `name` 추가(SignalService N+1 회피 배치조회). 프론트 `<InstrumentLabel>`로 movers/김프/검색/관심/종목·신호상세/카드에서 **이름 크게 + 심볼 작게**. 코인=한글명(비트코인/리플), 미국=영문명(Apple/NVIDIA), 한국=한글명(삼성전자).
- **부작용 정리**: 전체 코인 동기화로 김프가 263행 → 홈 김프 **상위 20 + "더 보기"** 토글로 제한.
- 검증: 에테나 검색 OK, 이름 DB/API 확인, 홈 스크린샷 이름우선+에테나 노출, build/typecheck/smoke 0에러.

### Round 8 — 미국/한국 movers + CoinGecko 트렌딩 (완료, 검증)
- 급등락이 4시장 모두 지원: US/KOSPI/KOSDAQ는 DB 일봉 2개로 24h 변동 계산(외부호출 없음). 홈 movers에 시장 토글(코인/미국/코스피/코스닥). 검증: US 급등 KLAC +5.55%·AMD +4.73%(영문명).
- `GET /market/trending`(CoinGecko /search/trending, 무키) → 홈 트렌딩 카드. 검증: SpaceX xStock·Bittensor 등.
- ⚠️ OneDrive가 `.next`를 동기화하며 청크 손상→404/500 회귀 발생. **`.next` 삭제 후 재기동**으로 복구(재발 시 동일 조치). smoke 0에러 복구.

### Round 9 — 관심종목 실시간 + 신호상세 성과 스트립 (완료, 검증)
- 백엔드 `/watchlists/default` 항목 보강: `last_price`(최신 캔들 종가)·`last_price_at`·`recent_signal`(최신 신호). 검증: 비트코인 96,241,000 + TRIANGLE/EXPIRED.
- 프론트: 관심종목 행에 이름우선+라이브가(WS 오버레이)+최근신호 배지. 신호상세 차트 위 성과 horizon 스트립(1h~7d 수익률 색상 pill).
- smoke 0에러(라이브).

### Round 10 — 종목 상세 차트 지표 오버레이 (완료, 검증)
- `lib/indicators.ts`(클라 계산: SMA·Bollinger), `ChartView`에 `overlays={ma,bollinger}` 라인 시리즈, 종목상세에 MA/볼린저 토글 칩. 무백엔드. build/typecheck/smoke 0에러.

### 검증 라운드 (Round 8~10 후) — 회귀 1건 수정
- 전체 엔드포인트 25개 스윕: `에테나` 검색은 URL 인코딩만 하면 200(정상), **파생 회귀 발견**: 전체 코인 265개 동기화 후 per-symbol OI 호출 30개 캡이 HashMap 무순서라 BTC/ETH 누락 → `DerivativesService`에 **메이저 우선순위(MAJORS) 정렬** 추가. 결과: BTC OI $6.27B·ETH $3.77B·SOL·XRP·DOGE·TRX, /derivatives/BTC 200.
- 최종: 서비스 3개 가동, 라이브 smoke 0에러, instruments 476·candles 246,899·signals 410.

### Round 11 — 속보 감성 + 종목 태깅 (완료, 검증)
- 백엔드 `NewsClassifier`(키워드 KO/EN 감성 POSITIVE/NEGATIVE/NEUTRAL + 코인 별칭 인덱스로 tagged_symbols, 무LLM, compute-on-read, 10분 캐시). `/news` 응답에 `sentiment`·`tagged_symbols` 추가.
- 프론트: 속보 카드에 감성 배지(긍정/부정/중립) + 종목 칩(KRW- 제거). 검증: 실 뉴스 "Former ECB…"·"카르다노…"에 감성/태그 렌더, smoke 0에러.
- ⚠️ **운영 발견**: 에이전트가 mock 모드로 띄운 stale dev 서버가 :3000을 점유 → 사용자가 mock을 보고 있었음. Next는 시작 시 `NEXT_PUBLIC_USE_MOCK`을 번들에 고정하므로, **:3000은 반드시 USE_MOCK=false로 시작한 서버여야 함**. `.next` 정리 후 재기동으로 실데이터 확정.

### Round 12 — 글로벌 시총 + 급등락/김프 딥링크 (완료, 검증)
- `GET /market/global`(CoinGecko /global 재사용): 총 시총·24h거래량·24h변동%·활성코인·BTC/ETH 도미넌스. 홈 상단 카드. 검증: 시총 $2.26T·17,463 코인.
- 급등락 행에 `instrument_id` 추가 + 급등락/김프 행 → `/instruments/[id]` 딥링크.
- ⚠️ digit snake_case 드리프트 재발: `market_cap_change24h_pct`(Java `…24hPct`→`…24h_pct`). 프론트 정렬. (change1d·trade_value24h·expected1x_pct와 동일 패턴 — Java 필드 숫자 앞엔 `_` 안 붙음. **신규 필드는 backend 실제 출력 확인 후 프론트 타입 작성.**)

### Round 13 — 속보 종목 칩 딥링크 + 오태깅 수정 (완료, 검증)
- 뉴스 DTO에 `tagged_instruments:[{symbol,instrument_id}]` 추가(NewsService가 symbol→id 인덱스로 해석). 프론트 `CoinChips`가 id 있으면 `/instruments/[id]` 링크(카드 링크와 중첩 방지 위해 칩은 형제 배치 + stopPropagation).
- **오태깅 수정**: 짧은 영단어 티커(IN·SOON·GAS 등)가 영문 prose에 매칭 → ASCII 티커 길이≥3 + 영어 불용어(STOP_WORDS) 제외. 검증: "Former ECB…"·"Prices Likely…" 태그 0, 실제 언급(ADA/USDT/XRP)만 태깅. smoke 0에러.

### Round 14 — 종목 상세 통합 패널 + 프론트 프로덕션 전환 (완료, 검증)
- 종목 상세에 **파생 패널**(CRYPTO만, `/derivatives/{base}` 펀딩·OI·롱숏, 무perp면 숨김) + **관련 속보**(`GET /news?symbol=KRW-XXX` — 태그 포함 항목만, 최근 윈도우 계산 후 필터). 검증: /news?symbol=KRW-XRP → XRP 태그 항목만.
- ⚠️ **프론트 :3000을 dev → 프로덕션(`next build && next start`)으로 전환**: dev의 온디맨드 재컴파일이 OneDrive의 `.next` 동기화와 충돌해 청크 404/500이 반복됨(사용자 간헐 오류 원인). 프로덕션 서버는 사전 빌드된 청크를 서빙해 안정적. **주의: 이후 프론트 코드 변경은 `npm run build` 후 재시작 필요(핫리로드 아님).** 검증: login 200, smoke 0에러.

### Round 15 — 백테스트 엔진 (기획서 §11, 완료·검증)
- `com.vein.backtest`: `POST /backtests/run {type,market?,timeframe?,target_pct,stop_pct,horizon,period_days?,fee_pct?}`. 저장 신호를 거래로 재생: 진입=탐지가, 청산=목표/손절(동일봉 양쪽이면 손절 보수적)/기간(TIME), 수수료 차감, 자산곡선 복리, 지표(거래수·승률·평균/누적수익·PF·MDD·best/worst·skipped). 무테이블(on-demand), 미래데이터 누수 없음.
- 프론트: 스캐너 3번째 서브탭 "백테스트" — 폼(타입/시장/TF/목표·손절·기간·수수료) + 지표 카드 + 자산곡선 + 거래 테이블(이름우선·WIN/LOSS/TIME). 
- 검증: TRIANGLE/CRYPTO 66거래·누적+135.66%·skipped34(후속캔들 부족). ABC는 신호 3개뿐이라 0(데이터 의존). 백엔드 단위테스트 6건, 프론트 build/typecheck/smoke 0에러.

### Round 16 — 전략 저장(§12 씨앗) + 사용자 TRIANGLE 제거 반영 (완료, 검증)
- 전략: V12 `strategies`(JSONB params/metrics) + CRUD(`POST/GET/GET{id}/DELETE /strategies`). 백테스트 패널에 "전략 저장"·"저장된 전략"(불러와 재실행/삭제). 검증: 저장→목록 200.
- **사용자가 TRIANGLE(삼각수렴) 기능을 의도적으로 제거 중**(하니스 "intentional, don't revert"): 프론트 SignalType/라벨/서브타입 + 백엔드 SignalType enum·TriangleDetector·`V9__remove_triangle.sql` 직접 편집. → 되돌리지 않고 **충돌만 해결**: ① 사용자의 V9가 기존 `V9__widen_tvl_change`와 버전충돌 → `V13__remove_triangle`로 리네임. ② TRIANGLE 제거가 `alerts.signal_type='TRIANGLE'`(과거 테스트 알림)를 놓쳐 `/alerts` 500 → 즉시 정리 + `V14__remove_triangle_alerts`(notifications unlink 후 삭제) 추가. 결과: 신호 ABC134·IMALOL106·TOP76(TRIANGLE 0), /alerts 200, smoke 0에러.
- 프론트 프로덕션 재빌드로 전략 UI + TRIANGLE 제거 배포.

### Round 17 — 워크포워드 백테스트 (기획서 §43, 완료·검증)
- `/backtests/run`에 `walk_forward`(+`is_ratio` 0.5~0.9, 기본 0.7): 거래를 시간순 IS/OOS 분할 → 각 Metrics + `overfit_warning`(OOS avg<0 while IS>0, 또는 OOS 승률 < IS−15pp). metrics 계산을 `computeMetrics()`로 추출 재사용. backtest 패키지만 수정(사용자 TRIANGLE 작업과 분리).
- 프론트: 백테스트 폼에 "워크포워드 검증" 토글 + IS/OOS 2열 비교 + "⚠ 과적합 주의" 배지.
- 검증: IMALOL 워크포워드 IS(+1.08%)/OOS(+0.58%) 분할·split_at·overfit 산출. build/typecheck/smoke 0에러.

### Round 18 — 저장 전략 성과 추적 (완료·검증)
- V15 `strategy_runs`(metrics JSONB + 비정규화 trade_count/total_return_pct/win_rate). `POST /strategies/{id}/run`(저장 params를 BacktestService.run 재사용 → 스냅샷 저장 + 부모 metrics 갱신), `GET /strategies/{id}/history`. 일 1회 스케줄러(gated)로 전 전략 재실행. 구/손상 params는 skip(WARN)/400.
- 프론트: 전략 행 "재실행" + 펼치면 성과 히스토리 스파크라인(total_return_pct) + 최신 스냅샷.
- 검증: 저장→run x2(200)→history 2건. build/typecheck/smoke 0에러. (한글 이름은 앱에서 정상 UTF-8 — curl 인코딩 한계로 테스트만 ASCII.)

### Round 19 — Binance 강제청산 WebSocket 스트림 (완료·라이브 검증)
- 백엔드: JDK 21 `java.net.http.WebSocket`으로 Binance USD-M 전시장 `!forceOrder@arr` 연결. 연결 끊김 시 5초 주기 재접속, COIN-M 프레임 제외, 체결가×수량으로 USD 청산금액 계산.
- 최근 500건 메모리 순환 버퍼 + `GET /liquidations?limit=&min_notional=&symbol=` 조회. `SELL=롱 청산`, `BUY=숏 청산` 정규화, 롱/숏/전체 합계 제공. 각 이벤트는 기존 STOMP `/topic/liquidations`에도 즉시 발행.
- 프론트 데이터 화면에 "청산" 탭 추가: `$10K+/$100K+/$1M+` 필터, LIVE 연결 상태, 롱·숏 청산 합계, 종목/체결가/수량/시각 목록. mock 계약도 동일하게 추가.
- 검증: 백엔드 `compileJava`/`compileTestJava` 통과, ASCII 임시 경로에서 `LiquidationServiceTest` 2건 통과, 프론트 `typecheck`/프로덕션 `build` 통과. 서비스 재기동 후 Binance 스트림 `connected=true`, 라이브 Chrome smoke 0에러.

### Round 20 — 웹 알림 채널 + 전달 추적 (완료·라이브 검증)
- 프론트: 설정 화면에 웹 알림 권한/활성화 토글 추가. 서비스 워커 `sw.js`를 등록하고, 앱 실행 중 미확인 알림을 30초 폴링으로 감지해 브라우저/설치형 PWA 알림으로 표시. 알림 클릭 시 신호 상세 또는 설정 알림함으로 이동. 최초 활성화 시 기존 알림은 기준점으로 저장해 과거 알림 폭주 방지.
- 백엔드: `POST /notifications/{id}/deliveries/web-push` 추가. 인증 사용자 소유권을 확인하고 `delivery_attempts`에 `WEB_PUSH/OK` 기록. V16에서 `(notification_id, channel)` 유니크 인덱스를 추가해 재시도·중복 확인을 멱등 처리.
- 제약: 외부 VAPID/FCM 키 없이 동작하는 단계라 앱/브라우저가 실행 중일 때만 폴링 후 표시한다. 완전 백그라운드 서버 푸시는 후속.
- 검증: 백엔드 `compileJava`/`compileTestJava`, 프론트 `typecheck`/프로덕션 `build` 통과. V16 적용 확인, 동일 알림 전달 확인 API 2회 호출 후 DB `WEB_PUSH` 1건 유지, `/sw.js` 200, 라이브 Chrome smoke 0에러.

### Round 21 — Pattern Score v1 + 규칙 기반 Explain (기획서 §9·§10·§40, 완료·라이브 검증)
- 탐지기의 기존 `score`는 패턴 구조 완성도 원본으로 유지하고, 별도 `GET /signals/{id}/explain`에서 종합 Pattern Score를 계산. 기획서 배점 그대로 완성도30·거래량20·추세20·변동성10·뉴스20이며 가중치는 `application.yml`로 분리.
- Explain은 실제 입력 지표에 연결된 탐지 근거, 무효화·변동성·뉴스 위험, 다음 확정봉 확인 포인트를 반환. `Risk Guard=PASS/WARN/BLOCK`을 별도 제공하고 매수·수익 확정 표현을 사용하지 않음.
- 과거 1d 성과는 점수에 섞지 않고 표본 수에 따라 `INSUFFICIENT/LOW/MEDIUM/HIGH` Confidence와 적중률·평균수익으로 별도 표시. 점수와 신뢰도를 하나로 합치지 않는 기획서 §40 원칙 반영.
- 프론트 신호 상세에 100점 카드, 5요소 진행 바, 위험·다음 확인·Confidence를 추가. 기존 detector score는 `구조 점수`로 명칭을 분리. mock API도 동일 계약.
- smoke가 기존에는 신호 상세를 방문하지 않던 공백을 발견해, 스캐너의 첫 실제 신호 링크를 따라가 상세 API·화면까지 매번 검증하도록 보강.
- 검증: 백엔드 컴파일 통과, ASCII 경로에서 `PatternScoreCalculatorTest` 3건 통과, 실 API에서 `pattern_score=요소 합계` 확인, 프론트 typecheck/build 통과, 최신 신호 상세 스크린샷 육안 확인, 라이브 smoke 0에러.

### Round 22 — Explain 피드백 + 품질 지표 (기획서 §35·§68, 완료·라이브 검증)
- V17 `explain_feedback`: 사용자·신호별 유용성 평가, 선택 사유, 생성/수정 시각 저장. `(user_id, signal_id)` 유니크로 반복 평가가 표본을 부풀리지 않게 하고 재선택은 기존 행 갱신.
- `GET /explain/{signalId}/feedback`은 전체 평가 수·유용 수·유용률과 현재 사용자 선택을 반환. `POST`는 `{helpful,reason?}` upsert, 부정 사유는 `UNCLEAR/INACCURATE/MISSING_RISK/TOO_COMPLEX/OTHER`로 제한.
- 신호 상세 Explain 하단에 `유용해요/아쉬워요` 버튼과 부정 사유 선택을 추가. 평가 후 유용률·평가 수를 즉시 갱신하며 mock도 동일 계약.
- 검증: V17 적용, 동일 사용자가 긍정→부정 재평가 시 유용률 100%→0%, `my_reason=MISSING_RISK`, DB 행 1개 유지. 백엔드 compile, 프론트 typecheck/build, 상세 스크린샷 육안 확인, 상세 포함 라이브 smoke 0에러.

### Round 23 — 조건검색기 v1 + 저장식 (기획서 §4.3·§6, 완료·라이브 검증)
- V18 `scanner_rules`: 사용자별 검색식 이름, 시장, timeframe, AND/OR, 조건 JSON, 활성 상태 저장. 소유권 기반 목록·저장·삭제 API 추가.
- `POST /scanner/run`: 활성 시장 유니버스를 대상으로 저장 캔들에서 RSI14, 거래량/20봉 평균, MA5/MA20, MACD 히스토그램을 계산해 최대 8개 조건을 AND/OR 평가. 지원식: RSI·VOLUME_RATIO·MACD_HISTOGRAM 대 숫자, PRICE·MA5 대 MA20. 데이터 부족 종목은 전체 요청을 실패시키지 않고 제외.
- 프론트 스캐너에 `조건검색` 탭 추가: 시장·봉·논리 선택, 조건 추가/삭제, 즉시 실행, 종목 상세 딥링크 결과, 검색식 저장·불러오기·삭제. mock 계약도 동일하게 구현.
- smoke에 조건검색 탭 진입과 실제 실행을 추가. 기존 백엔드 프로세스 종료로 1회 연결거부가 발생했으나 재기동 후 동일 smoke 0에러.
- 검증: CRYPTO 1d에서 257종을 약 735ms에 평가, 테스트 조건 37종 반환. 저장→목록→삭제 성공. V18 적용, 백엔드 compile, 프론트 typecheck/build, 모바일 스크린샷 육안 확인, 조건 실행+신호 상세 포함 라이브 smoke 0에러.

## 자율 개선 백로그 (다음 라운드 후보)
1. 조건검색 저장식 알림·발생 빈도 시뮬레이션.
2. 청산 스트림 시간대별 집계·급증 감지.
3. 기본 Admin 대시보드.

## 검증 도구
- `frontend/scripts/smoke.mjs` — Chrome(puppeteer-core) 로그인→전 라우트→콘솔/페이지 에러·스크린샷. 스크린샷: `%LOCALAPPDATA%\Temp\vein_shots\`. 매 변경 후 `node scripts/smoke.mjs`로 회귀 확인.
