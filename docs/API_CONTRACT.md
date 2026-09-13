# VEIN 모바일 — API 계약 v2 (단일 진실 소스 · 레거시 11탭 / 4시장 전이식)

> 출처: 레거시 `C:\veni_invest_abc` (main.py 11탭, config/constants, core 탐지기) + `docs/LEGACY_FEATURE_MAP.md`.
> backend(Spring Boot) · frontend(Next.js) · app(Flutter) 가 **이 문서를 동일하게 따른다.** v1(알파)에서 확장됨.

## 공통 규약 (v1과 동일)
- Base `/api/v1`, JSON `snake_case`, 시간 ISO-8601 UTC(`Z`), 가격·수량은 **문자열 Decimal**.
- 인증 `Authorization: Bearer`, access 만료 시 `/auth/refresh` 회전.
- 목록 cursor 기반 최신순, `page_size` 기본 20·최대 100.
- 성공 `{data, meta:{trace_id, freshness, next_cursor?}}`, 오류 `{error:{code,message,trace_id,field_errors}}`.
- `freshness`: `FRESH | DELAYED` (collected_at 경과 > timeframe×2 → DELAYED).
- **클라이언트 오류는 4xx로 정규화(R43·R49·R78)**: 잘못된 파라미터 타입/enum·누락 파라미터·바디 검증 실패·깨진 JSON → `400 VALIDATION_ERROR`(+field_errors), 잘못된 메서드 → `405 METHOD_NOT_ALLOWED`, 인증 통과 후 미매핑 경로 → `404 NOT_FOUND`(R49), 권한 부족(비관리자의 `/admin/**` 등) → `403 FORBIDDEN`(R78). 500은 실제 서버 오류에만(정규화된 4xx는 ERROR 로그 미출력).

## enum (확장)
- **market**: `CRYPTO | US | KOSPI | KOSDAQ`
- **timeframe**: 코인 `15m|1h|4h|1d|3d|1w|1M`, 주식 `1d|3d|1w` (서버가 market별 지원셋 반환)
- **signal.type (패턴 3종)**: `ABC | TOP | IMALOL`
  - ※ `TRIANGLE`(삼각수렴)은 **의도적으로 제거됨**(V13/V14). 탐지기·enum·DB 데이터 모두 정리 완료.
- **signal.status**: `DETECTED | NEAR_COMPLETION | INVALIDATED | EXPIRED | CLOSED`
- **evidence.type**: `PIVOT_0 | PIVOT_A | PIVOT_B | C_TARGET | BOLL_UPPER | BOLL_MID | BOLL_LOWER | MATCH_BOX`
  - (`TREND_UPPER`/`TREND_LOWER`는 TRIANGLE 전용이었으므로 더 이상 생성되지 않음)
- ~~**triangle subtype**~~: 제거됨 (`subtype`은 항상 NULL)
- **news.source**: `TELEGRAM | BLOOMBERG`
- **tvl.mode**: `PROTOCOL | CHAIN`

---

## 1. 인증/사용자 (v1 유지 + 공개 가입)
| Method | Path | 설명 |
|---|---|---|
| POST | `/auth/signup` | **비로그인 공개 가입**(MONETIZATION 단계2 ①). `{email, password(8~100), signup_source?, signup_referrer?}`. `vein.signup.auto-approve`(기본 true)면 `{status:APPROVED, user, access_token, refresh_token, expires_in}`(자동 로그인), false면 `{status:PENDING, user}`(토큰 없음). 중복 이메일 `409 ALREADY_EXISTS`, 비번 짧으면 `400 VALIDATION_ERROR`. **IP당 시간당 10건**(초과 `429 RATE_LIMITED`) |
| POST | `/auth/login` | `{email,password}`→`{access_token,refresh_token,expires_in,user}` |
| POST | `/auth/refresh` | 토큰 회전 |
| POST | `/auth/logout` | 204 |
| GET | `/me` | 내 정보 |

## 2. 터미널 (홈) — `market` 모듈
| Method | Path | 설명 |
|---|---|---|
| GET | `/market/indices` | 공포탐욕(value,classification), BTC도미넌스, USDT도미넌스, 알트지수, 나스닥/코스피/코스닥 지수 |
| GET | `/market/kimchi-premium` | `?sort=` 업비트(KRW) vs 바이낸스(USDT) 김프 목록(symbol, upbit_price, binance_price, usdkrw, premium_pct), BTC/ETH/XRP 상단고정 |
| GET | `/instruments` | `?q=&market=&status=` 4시장 통합검색(초성검색 포함), 최대 30 |
| GET | `/instruments/{id}` | 종목 상세 메타 |
| GET | `/instruments/{id}/candles` | `?timeframe=&from=&to=&limit=` 정렬 OHLCV + provider + freshness |
| GET | `/instruments/{id}/indicators` | `?timeframe=` RSI/MA(5/20/60/120)/볼린저(20,2)/MACD 요약 |

## 3. 관심종목(즐겨찾기) — v1 유지, market 무관 통합
| GET `/watchlists/default` | POST/DELETE `/watchlists/default/items[/{instrument_id}]` |

## 4. 스캐너 (패턴 3종 통합) — `signal`/`scanner`/`scalp`
| Method | Path | 설명 |
|---|---|---|
| GET | `/signals` | `?type=ABC\|TOP\|IMALOL & market & timeframe & instrument_id & watchlist_only & status & near_only & active_only & cursor`. **`active_only=true`(R54)**면 DETECTED/NEAR_COMPLETION만(만료·무효 숨김). 기본 false(하위호환). 카드: type, market, instrument, timeframe, status, score, current_price, **c_target(ABC/TOP=C목표가·IMALOL=C예상가/projectedClose, R80)**, pivots 요약(0/A/B 일자), detected_at, freshness |
| GET | `/signals/top` | 오늘의 주목 신호(R41): `?market=&limit=`(기본 10·최대 30). 활성 신호(DETECTED/NEAR_COMPLETION)를 **종합 Pattern Score 상위**로 정렬(R90: `coalesce(pattern_score, score)` — 완성도+거래량+추세+변동성+뉴스 종합, 미계산 시 구조점수 폴백) + 종목별 dedup(R81) + 만료 제외(R82). 카드 형식은 `/signals`와 동일 |
| GET | `/signals/{id}` | evidence(피벗/추세선/볼린저/매치박스), invalidation, c_target, chart_range, algorithm_version, **event_risk(R39, nullable)**, **expires_at(R85, nullable — 유효기간/만료 시각)** |
| GET | `/signals/{id}/explain` | Pattern Score(완성도30·거래량20·추세20·변동성10·뉴스20), 규칙 기반 근거·위험·다음 확인, Risk Guard(PASS/WARN/BLOCK), 1d 성과 표본 기반 Confidence |
| GET | `/signals/{id}/performance` | 이 신호의 탐지 후 실현 성과(§31): `{signal_id, detected_price, detected_at, horizons[]{horizon(1h\|4h\|1d\|3d\|7d), price, return_pct, mfe_pct, mae_pct, evaluated_at}}`. `horizons`는 경과·계산된 것만(너무 신선하면 빈 배열, 500 없음) |
| GET | `/signals/performance/summary` | 패턴 유형별 과거 실측 집계(해자): `?type&market&timeframe&horizon(기본 1d)&from&to&bucket=MONTH`. `rows[]{type,market,timeframe,bucket?(yyyy-MM, MONTH일 때만),horizon,sample_size,hit_rate,avg_return_pct,median_return_pct,avg_mfe_pct,avg_mae_pct}`(표본순). `hit_rate`=수익>0 표본 비율. 스캐너 성과 스트립·**신호 상세 "이 패턴 과거 성과" base-rate(R86)**·Explain Confidence가 사용. from/to는 detected_at 반개구간, 미매칭 시 빈 배열 |
| GET/POST | `/explain/{signalId}/feedback` | Explain 유용성 집계·내 평가 조회 / `{helpful,reason?}` 사용자별 평가 upsert. **`helpful`은 필수(Boolean) — 누락/null이면 `400`(R79)**. reason=`UNCLEAR|INACCURATE|MISSING_RISK|TOO_COMPLEX|OTHER` |
| GET | `/scalp/ranking` | 틱띄기: `?limit=` 업비트 24h 거래대금 상위 마켓 스캘핑 점수 랭킹(symbol, scalp_score, spread_ticks, tps, micro_vol, ob_imbalance, wall_state). 서버 WS 수집값 폴링 |
| GET | `/scalp/{symbol}` | 상세: 최근 체결 흐름(매수/매도 비율), 호가 상위레벨, 벽/취소의심 |
| POST | `/scanner/run` | 조건검색 즉시 실행 `{market,timeframe,logic,conditions[]}`. v1 지표: RSI, VOLUME_RATIO, PRICE→MA20, MA5→MA20, MACD_HISTOGRAM |
| GET/POST/PATCH/DELETE | `/scanner/rules[/{id}]` | 사용자별 조건검색식 목록·저장·수정(활성 토글)·삭제. **저장(POST)은 FREE 플랜 최대 3개**(초과 `402 PLAN_LIMIT_EXCEEDED`, R52). 빈/부분 바디는 `400`(R58) |
| POST | `/scanner/rules/{id}/simulate` | 현재 스냅샷에 저장식 실행 → 평가/매칭 수·매치율·빈도등급(LOW/MEDIUM/HIGH)·표본 |
| GET | `/scanner/rules/{id}/history` | 저장식 최근 실행 이력 |

스캐너 파라미터(서버 고정, 노출용): ABC `MIN_A_DROP_PCT=0.20, MIN_B_RETRACE=[0.236,0.886], LOCAL_WIN=5, SEARCH_WINDOW=100, MIN_0_PROMINENCE=0.10, STRICT_WAVE=true, MAX_PATTERNS=3`. 재스캔: 주/3일/일 4h마다, 4h/1h/15m 각 주기.

- **무효화 완충(R53·R77)** — `GET /signals/{id}`의 `invalidation` = `{rule, price, buffer_pct, effective_price}`. 저가-이탈 규칙(ABC A저점·TOP B저점)은 `price`(기준선)를 1틱만 깨도 죽지 않고 **`effective_price = price × (1 − buffer_pct)`** 아래로 저가가 내려가야 무효 처리(노이즈/꼬리 흡수). `buffer_pct`는 `vein.signal.invalidation-buffer-pct`(기본 0.03) 파생. 완충 없는 규칙은 `buffer_pct`/`effective_price` 둘 다 null. FE 카드는 R:R 거리를 `effective_price` 우선으로 계산.

## 4-1. 검증 도구 (백테스트·전략·모의투자) — `backtest`/`strategy`/`paper`
> R92 문서화: 그동안 계약서에 누락됐던 실동작 엔드포인트. 코드(`*Controller`/`*Dto`)와 정적 대조.

| Method | Path | 설명 |
|---|---|---|
| POST | `/backtests/run` | 패턴 백테스트(기획서 §11): `{type(ABC\|TOP\|IMALOL), market?, timeframe?, targetPct, stopPct, horizon(1h\|4h\|1d\|3d\|7d), periodDays?, feePct?, walkForward?, isRatio?}`. 응답 `{params, metrics{trade_count,win_rate,avg_return_pct,total_return_pct,profit_factor,max_drawdown_pct,best_pct,worst_pct,avg_hold_bars,skipped}, equity_curve[]{t,equity}, trades[]{symbol,name,detected_at,entry,exit,return_pct,outcome,exit_at}, walk_forward?{is_ratio,split_at,in_sample,out_of_sample,overfit_warning}}`. `walk_forward`는 요청 `walk_forward=true`일 때만(과적합 경고). |
| GET/POST | `/strategies` | 저장 전략 목록 / 저장 `{name, params(JSON), metrics(JSON)}`. 응답 `{id,name,type,market,timeframe,params,metrics,created_at}` |
| GET/DELETE | `/strategies/{id}` | 단건 조회 / 삭제(소유자만·204) |
| POST | `/strategies/{id}/run` | 저장 파라미터로 백테스트 재실행 → 성과 스냅샷 적재. 응답 `RunSnapshot{id,strategy_id,metrics,trade_count,total_return_pct,win_rate,run_at}` |
| GET | `/strategies/{id}/history` | 재실행 성과 스냅샷 이력(시간순) `RunSnapshot[]` |
| POST | `/paper/accounts` | 모의계정 생성 or 리셋(create-or-reset) `{initial_balance, base_currency}`. 응답 `AccountResponse{id,base_currency,initial_balance,...}` |
| POST | `/paper/orders` | 모의 주문(즉시 체결) `{instrument_id, signal_id?, side(BUY\|SELL), type(MARKET\|LIMIT), investment_type(SPOT\|FUTURES), position_side(LONG\|SHORT)?, price?(비우면 최신가), quantity, leverage?, reduce_only?, timeframe?}`. 응답 `OrderResponse{id,account_id,instrument_id,symbol,name(R91),signal_id,investment_type,position_side,side,type,price,quantity,leverage,reduce_only,status,fill?}`. 잔액부족·보유초과 등 `400` |
| GET | `/paper/portfolio` | `{account, equity, unrealized_pnl, realized_pnl, positions[]{instrument_id,symbol,name,quantity,investment_type,position_side,avg_price,mark_price,market_value,margin,leverage,unrealized_pnl,realized_pnl}, recent_orders[](OrderResponse, 최근 20)}` |
| GET | `/paper/performance` | `{account_id, total_return_pct, equity, realized_pnl, unrealized_pnl, open_positions}` |

- **컴플라이언스** — 모의투자는 가상 현금·포지션만 기록(실거래소 주문·실자금 없음). 신호 상세 "다음 액션"이 여기로 연결(코인=선물 롱/숏+레버리지, 주식=현물 매수, R84).

## 5. 속보 — `news`
| GET | `/news` | `?source=TELEGRAM\|BLOOMBERG & cursor` 항목(source, title, body, url, published_at, is_new). 서버가 텔레그램(coinnesskr)+Bloomberg RSS 수집·중계, 앱은 읽기 |

## 6. 데이터 (온체인/섹터/차익) — `tvl`/`supply`/`theme`/`funding`
| Method | Path | 설명 |
|---|---|---|
| GET | `/tvl` | `?mode=PROTOCOL\|CHAIN & sort=TVL\|CHANGE_7D & q=` (순위,이름,category,체인,tvl,mcap,change_1d,change_7d) TVL<$1000 제외 |
| GET | `/tvl/{id}/history` | 프로토콜 TVL 히스토리 라인 |
| GET | `/supply` | `?sort=&q=` CoinGecko 유통량(rank,name,symbol,price_usd,market_cap,circulating,total,max,circulating_pct,fdv) |
| GET | `/themes` | `?market=CRYPTO\|US\|KR & q= & unclassified_only & low_confidence_only` 테마목록 |
| GET | `/themes/{id}/constituents` | 구성종목 + 분류 source/confidence |
| GET | `/funding-arb` | `?sort=` 업비트 현물 vs Bybit 선물 펀딩비 차익(symbol, funding_pct, upbit_price, bybit_price, next_funding_at, expected_1x_pct, expected_2x_pct). 수수료 Upbit 0.05%/Bybit 0.055% 왕복 반영 |

## 7. 알림 — `alert`/`notification` (패턴 3종 + 가격 지원)
| GET `/alerts` (목록) | POST `/alerts` `{instrument_id, signal_type(ABC/TOP/IMALOL), timeframe, market, cooldown_sec}` | PATCH `/alerts/{id}` `{enabled,cooldown_sec}` | **DELETE `/alerts/{id}`**(R62, 소유자만·404 IDOR-safe) |
| POST `/alerts` **FREE 플랜 최대 10개**(초과 `402 PLAN_LIMIT_EXCEEDED`, R56) |
| GET `/notifications?unread_only&cursor` | PATCH `/notifications/{id}/read` · **POST `/notifications/read-all`**(활성 안읽음 일괄 읽음→`{updated}`, R63) · POST `/notifications/{id}/deliveries/web-push` (브라우저 표시 성공 멱등 확인) |
| GET `/notifications/digest?window=` | 읽기 시점 요약(R45·R51): 창(시간, 기본 24·최대 168) 내 알림을 분류(SIGNAL/SCANNER/LIQUIDATION/SYSTEM)별 집계 + 안읽은 최신 표본 + `released`(스풀링 방출 수) + 요약 문장. 보류 중(held_until>now)은 제외 |
| GET/PUT `/me/notification-prefs` | 조용한 시간(R42): `{quiet_enabled, quiet_start_hour, quiet_end_hour}`(KST 0-23). 창 동안 온 알림은 **드롭이 아니라 보류(R46 스풀링)**, 창 종료 시 방출. start>end면 자정 넘김, start==end면 창 없음 |
| GET `/me/onboarding` · POST `/me/onboarding/dismiss` | "시작하기" 체크리스트(R48): 관심종목·알림·모의투자·조건검색 스텝 완료를 실제 데이터에서 파생 + 진행률 + 닫힘. 스텝: `{key,label,done,href}` |

## 8. 시스템 — `ops`
| GET `/system/status` | `providers[]{provider, freshness(FRESH\|DELAYED\|UNKNOWN), source(REAL\|STUB), last_run_at}` + `scanner_status` + `build_version` + `sidecar{healthy,url}`. provider: upbit/binance/coingecko/defillama/yfinance/pykrx/bybit/telegram/bloomberg/binance_futures |

- **`source`(R40)** — `STUB`이면 그 provider가 현재 **합성 스텁 폴백** 중(실데이터 아님). 사이드카 경유 provider(yfinance·pykrx·telegram)는 `sidecar.healthy=false`일 때 STUB, keyless 직결 provider는 항상 REAL. FE 설정 "데이터 출처"에 실데이터/합성 배지 + 스텁 경고로 노출. `SidecarHealth`가 30s 캐시.

## 8-1. 거시경제 / 시장국면 — `macro` (§15.1·§39)
| Method | Path | 설명 |
|---|---|---|
| GET | `/macro` | 스냅샷: `regime`(항상) + `yield_curve`·`m2`·`dxy`(FRED 키 있을 때) + `sources[]` + `generated_at`. ~10분 캐시 |
| GET | `/macro/regime` | 국면만: `{label(BULL\|BEAR\|RANGE\|TRANSITION), score, summary, signals[]{key,direction(BULLISH\|BEARISH\|NEUTRAL),detail}}` |
| GET | `/macro/calendar` | 경제 캘린더(R39): `?days=1..90(기본 14)`. `{events[]{date(yyyy-mm-dd),dday,type(FOMC\|CPI\|EMPLOYMENT),title,region,impact}, generated_at}`. 오늘 근접(\|D-day\|) 순 정렬 |

- **국면은 내부 데이터로 항상 판정** — 나스닥 지수 추세(최근 ~20p 모멘텀) + 공포탐욕(위험선호/회피). 두 신호의 방향 합산 점수로 라벨(±2 이상 BULL/BEAR, 혼재 TRANSITION, 그 외 RANGE).
- **FRED 키(`FRED_API_KEY`) 설정 시** 금리차(DGS2/DGS3MO/DGS10 → 10Y-2Y·10Y-3M·역전여부), M2(M2SL, YoY), 달러인덱스(DTWEXBGS, 추세)를 보강하고 각각 국면 신호로 반영. 미설정 시 해당 블록 null·조용히 생략(사이드카 스텁-폴백과 동일).
- **경제 캘린더(R39, keyless)** — FOMC·CPI 발표일은 **큐레이션 고정일 상수**, 고용보고서(NFP)는 **매월 첫째 금요일 규칙**으로 생성. 외부 키·호출 없음. 위험자산 전반에 영향하므로 코인 포함 전 시장 공통 라벨로 취급.
- **신호 이벤트 리스크(R39)** — `GET /signals/{id}` 상세에 `event_risk`(nullable) 추가: `{active, level(HIGH\|MEDIUM), confidence_delta(음수 참고값), note, events[]{date,dday,type,title}}`. 매크로 이벤트(D-1~D+1 HIGH) + 주식 실적발표(D-7~D-day, 사이드카 yfinance `/equity/earnings` 경유·폴백 null). 저장 점수는 불변, **읽기 시점 힌트**로만 노출. 임박 이벤트 없으면 null.

## 9. 공개 콘텐츠 (비로그인) — `report`
| Method | Path | 설명 |
|---|---|---|
| GET | `/public/reports/weekly` | 주간 패턴 성과 리포트(JSON). 누적 신호 성과에서 **패턴×시장×봉** 단위 적중률·평균/중앙 수익률 집계. `?window_days=1..365(기본 90) & horizon=1h\|4h\|1d\|3d\|7d(기본 1d)`. 응답: `overall{sample_size,hit_rate,avg_return_pct}`, `rows[]{type,market,timeframe,sample_size,hit_rate,avg_return_pct,median_return_pct}`(표본순), `highlights[]`(표본 5+ 최고/최저), `disclaimer` |
| GET | `/public/reports/weekly.md` | 동일 리포트 **Markdown 본문**(블로그·SNS 발행용, `text/markdown`) |

- **비로그인 공개** — `SecurityConfig`에서 `/api/v1/public/**` permitAll. **개별 신호·종목·원시 캔들은 미노출**(가공 통계만) — 법적 안전지대와 상업 논리가 같은 방향(MONETIZATION 단계2 ②).
- **레이트리밋** — `PublicRateLimitFilter`: IP당 **60 req/min** 고정창, 초과 시 `429 RATE_LIMITED`(+`Retry-After: 60`). 현재 API 전체 중 이 경로에만 적용.

## 10. 구독/운영 — `billing`/`admin` (Track C 착수)
| Method | Path | 설명 |
|---|---|---|
| GET | `/me/entitlements` | 구독 엔타이틀먼트(R52·R60): `{tier(FREE\|PRO), pro, features[]{key,label,limit(-1=무제한),used}}`. 한도는 티어 파생(FREE: 저장식 3·알림 10 / PRO 무제한). `used`는 현재 사용 수 |
| PATCH | `/admin/users/{id}/tier` | (운영자) 사용자 티어 설정 `{tier:FREE\|PRO}`(그 외 400). 감사 로그 `USER_TIER_UPDATE`. 결제 없이 PRO 부여/회수(R56) |
| GET | `/admin/overview` | 운영 개요. `users{total, by_status, by_tier(R72)}` + alerts/notifications/explain/scanner_rules/signals/instruments 요약 |
| PATCH | `/admin/users/{id}/approve\|lock` | 사용자 승인/잠금(감사 로그) |
| GET | `/admin/users \| audit-logs \| notifications \| delivery-attempts/failed` | 사용자 목록·감사·최근 알림·실패 전달 |

- **결제 미연동** — 티어는 운영자가 수동 부여(`PATCH .../tier`)하며 게이트(저장식·알림 한도)는 실집행(`402 PLAN_LIMIT_EXCEEDED`). 실 PG·자동 승급은 후속.

---

## DB 추가 테이블 (Flyway V6+, 기존 V1~V5 유지)
- **V6 market/terminal**: `market_indices`(key,value,classification,collected_at), `kimchi_premium`(instrument_id,upbit_price,binance_price,usdkrw,premium_pct,collected_at)
- **V6 instruments 확장**: market enum에 US/KOSPI/KOSDAQ 추가, `provider_symbols`에 BINANCE/YFINANCE/PYKRX 매핑
- **V7 signal 확장**: `pattern_signals.type` ABC/TOP/IMALOL, `c_target` NUMERIC, evidence 타입 확장(볼린저/매치박스)
- **V13/V14 triangle 제거**: TRIANGLE 신호·evidence·성과·알림 정리, `subtype` 미사용
- **V8 news**: `news_items`(id,source,title,body,url,published_at,collected_at,uq(source,url))
- **V8 tvl/supply**: `tvl_snapshots`, `supply_snapshots`
- **V8 theme**: `themes`, `theme_constituents`(classification_source,confidence)
- **V8 funding/scalp**: `funding_arb_snapshots`, `scalp_scores`(symbol,scalp_score,components,collected_at)

가격·수량 NUMERIC, 시간 TIMESTAMPTZ(UTC). 패턴 탐지기는 순수 Java, 골든 fixture로 검증.

---

## 모바일 IA (하단탭 5 + 스택 화면)
1. **홈/터미널** `/` — 시장지표 카드(공포탐욕·도미넌스·김프) · 통합검색 · 관심(즐겨찾기) · 종목→차트
2. **스캐너** `/scanner` — 패턴 3종 필터(type/market/timeframe/near_only) · 결과카드 · 상세 오버레이차트 · 틱띄기 서브탭
3. **속보** `/news` — 텔레그램/Bloomberg 피드
4. **데이터** `/data` — TVL · 유통량 · 테마/섹터 · 펀비차익 (서브탭)
5. **설정** `/settings` — 알림규칙 · 데이터출처 · 진단

스택: 종목상세`/instruments/[id]`, 신호상세`/signals/[id]`, TVL상세, 테마구성종목, 스캘핑상세.
필수 상태(전 화면): Loading·Empty·Stale·Partial·Fatal·Offline. 컴플라이언스 "투자 참고용·투자권유 아님" 노출.
차트 오버레이: ABC/TOP=0-A-B 피벗 markers + C예상가 priceLine, IMALOL=볼린저밴드 + 매치박스.
