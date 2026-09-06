# VEIN 모바일 — API 계약 v2 (단일 진실 소스 · 레거시 11탭 / 4시장 전이식)

> 출처: 레거시 `C:\veni_invest_abc` (main.py 11탭, config/constants, core 탐지기) + `docs/LEGACY_FEATURE_MAP.md`.
> backend(Spring Boot) · frontend(Next.js) · app(Flutter) 가 **이 문서를 동일하게 따른다.** v1(알파)에서 확장됨.

## 공통 규약 (v1과 동일)
- Base `/api/v1`, JSON `snake_case`, 시간 ISO-8601 UTC(`Z`), 가격·수량은 **문자열 Decimal**.
- 인증 `Authorization: Bearer`, access 만료 시 `/auth/refresh` 회전.
- 목록 cursor 기반 최신순, `page_size` 기본 20·최대 100.
- 성공 `{data, meta:{trace_id, freshness, next_cursor?}}`, 오류 `{error:{code,message,trace_id,field_errors}}`.
- `freshness`: `FRESH | DELAYED` (collected_at 경과 > timeframe×2 → DELAYED).

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
| GET | `/signals` | `?type=ABC\|TOP\|IMALOL & market & timeframe & instrument_id & watchlist_only & status & near_only & cursor`. 카드: type, market, instrument, timeframe, status, score, current_price, **c_target(ABC/TOP)**, pivots 요약(0/A/B 일자), detected_at, freshness |
| GET | `/signals/{id}` | evidence(피벗/추세선/볼린저/매치박스), invalidation, c_target, chart_range, algorithm_version |
| GET | `/signals/{id}/explain` | Pattern Score(완성도30·거래량20·추세20·변동성10·뉴스20), 규칙 기반 근거·위험·다음 확인, Risk Guard(PASS/WARN/BLOCK), 1d 성과 표본 기반 Confidence |
| GET/POST | `/explain/{signalId}/feedback` | Explain 유용성 집계·내 평가 조회 / `{helpful,reason?}` 사용자별 평가 upsert. reason=`UNCLEAR|INACCURATE|MISSING_RISK|TOO_COMPLEX|OTHER` |
| GET | `/scalp/ranking` | 틱띄기: `?limit=` 업비트 24h 거래대금 상위 마켓 스캘핑 점수 랭킹(symbol, scalp_score, spread_ticks, tps, micro_vol, ob_imbalance, wall_state). 서버 WS 수집값 폴링 |
| GET | `/scalp/{symbol}` | 상세: 최근 체결 흐름(매수/매도 비율), 호가 상위레벨, 벽/취소의심 |
| POST | `/scanner/run` | 조건검색 즉시 실행 `{market,timeframe,logic,conditions[]}`. v1 지표: RSI, VOLUME_RATIO, PRICE→MA20, MA5→MA20, MACD_HISTOGRAM |
| GET/POST/DELETE | `/scanner/rules[/{id}]` | 사용자별 조건검색식 목록·저장·삭제 |

스캐너 파라미터(서버 고정, 노출용): ABC `MIN_A_DROP_PCT=0.20, MIN_B_RETRACE=[0.236,0.886], LOCAL_WIN=5, SEARCH_WINDOW=100, MIN_0_PROMINENCE=0.10, STRICT_WAVE=true, MAX_PATTERNS=3`. 재스캔: 주/3일/일 4h마다, 4h/1h/15m 각 주기.

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
| GET `/alerts` (목록) | POST `/alerts` `{instrument_id, signal_type(ABC/TOP/IMALOL), timeframe, market, cooldown_sec}` | PATCH `/alerts/{id}` `{enabled,cooldown_sec}` |
| GET `/notifications?unread_only&cursor` | PATCH `/notifications/{id}/read` · POST `/notifications/{id}/deliveries/web-push` (브라우저 표시 성공 멱등 확인) |

## 8. 시스템 — `ops`
| GET `/system/status` | provider별 freshness(upbit/binance/coingecko/defillama/yfinance/pykrx/bybit/telegram/bloomberg), scanner status, build version |

## 8-1. 거시경제 / 시장국면 — `macro` (§15.1·§39)
| Method | Path | 설명 |
|---|---|---|
| GET | `/macro` | 스냅샷: `regime`(항상) + `yield_curve`·`m2`·`dxy`(FRED 키 있을 때) + `sources[]` + `generated_at`. ~10분 캐시 |
| GET | `/macro/regime` | 국면만: `{label(BULL\|BEAR\|RANGE\|TRANSITION), score, summary, signals[]{key,direction(BULLISH\|BEARISH\|NEUTRAL),detail}}` |

- **국면은 내부 데이터로 항상 판정** — 나스닥 지수 추세(최근 ~20p 모멘텀) + 공포탐욕(위험선호/회피). 두 신호의 방향 합산 점수로 라벨(±2 이상 BULL/BEAR, 혼재 TRANSITION, 그 외 RANGE).
- **FRED 키(`FRED_API_KEY`) 설정 시** 금리차(DGS2/DGS3MO/DGS10 → 10Y-2Y·10Y-3M·역전여부), M2(M2SL, YoY), 달러인덱스(DTWEXBGS, 추세)를 보강하고 각각 국면 신호로 반영. 미설정 시 해당 블록 null·조용히 생략(사이드카 스텁-폴백과 동일).

## 9. 공개 콘텐츠 (비로그인) — `report`
| Method | Path | 설명 |
|---|---|---|
| GET | `/public/reports/weekly` | 주간 패턴 성과 리포트(JSON). 누적 신호 성과에서 **패턴×시장×봉** 단위 적중률·평균/중앙 수익률 집계. `?window_days=1..365(기본 90) & horizon=1h\|4h\|1d\|3d\|7d(기본 1d)`. 응답: `overall{sample_size,hit_rate,avg_return_pct}`, `rows[]{type,market,timeframe,sample_size,hit_rate,avg_return_pct,median_return_pct}`(표본순), `highlights[]`(표본 5+ 최고/최저), `disclaimer` |
| GET | `/public/reports/weekly.md` | 동일 리포트 **Markdown 본문**(블로그·SNS 발행용, `text/markdown`) |

- **비로그인 공개** — `SecurityConfig`에서 `/api/v1/public/**` permitAll. **개별 신호·종목·원시 캔들은 미노출**(가공 통계만) — 법적 안전지대와 상업 논리가 같은 방향(MONETIZATION 단계2 ②).
- **레이트리밋** — `PublicRateLimitFilter`: IP당 **60 req/min** 고정창, 초과 시 `429 RATE_LIMITED`(+`Retry-After: 60`). 현재 API 전체 중 이 경로에만 적용.

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
