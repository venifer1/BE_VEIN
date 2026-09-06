# 레거시(베니인베스트 터미널 v1.3.0) → VEIN 모바일 기능 매핑

> 출처: `C:\veni_invest_abc` (main.py 탭 구성, config.py/constants.py 파라미터, README, core/* 탐지기).
> 목적: 데스크톱 11탭 통합 터미널을 모바일로 재구성하기 위한 정확한 기능 인벤토리와 매핑.

## 레거시 실제 메뉴 (main.py `notebook.add` 순서)
1. **터미널** — 시장지표(공포탐욕/BTC·USDT 도미넌스/알트지수, 김치프리미엄) · 4시장 통합 종목검색 · 캔들차트(Binance, 봉 15m/1h/4h/1d/1w/1M, 1·2·4분할 멀티뷰, 크로스헤어/툴팁) · 주식·지수 차트(yfinance/pykrx) · 즐겨찾기(terminal_favorites)
2. **속보** — 텔레그램 채널(coinnesskr) + Bloomberg Markets RSS, 15초 자동갱신, 신규감지 시 Windows TTS, Telethon 로그인
3. **ABC 조정 검색기** — 4시장(업비트/미국장/코스피/코스닥) 자동스캔, C예상가 근접(<5%) 필터+TTS, 결과테이블(봉/종목/C예상가/현재가/0·A·B 일자), 차트 오버레이(0-A-B 파형+C수평선)
4. **고점판독기** — ABC 역변환(top_detector: invert→ABC→재역변환), 필터(현재가≥B, C>현재가, B저점 미이탈)
5. **이말올** — ABC 변형: 연속양봉2 + 2번째저가<1번째저가 + 볼린저하단(20,2.0) 접촉 + 2번째 거래량> + 20일선 위 제외 + 최근3봉 상승봉 제외. 동일종목+동일TF 1행 병합
6. **삼각수렴 검색기** — 대칭/상승/하락 삼각형, 결과(봉/종목/유형/현재가), 변곡점 H/L 연결 오버레이
7. **펀비차익/틱띄기** — (펀비) 업비트 KRW현물 vs Bybit USDT선물 펀딩비 차익, 기대1회/2회 수익(수수료 Upbit 0.05%/Bybit 0.055%) · (틱띄기) 업비트 WS orderbook+trade 실시간 스캘핑 점수(Spread/TPS/MicroVol/호가불균형) 랭킹
8. **테마/섹터** — 코인/미국/한국 테마분류(JSON맵+휴리스틱), 좌 테마목록·우 구성종목, 미분류/저신뢰 필터
9. **TVL** — DefiLlama 프로토콜/체인 TVL + 히스토리 라인차트, 60초 갱신
10. **유통량** — CoinGecko coins/markets 유통량/유통률/FDV, 헤더정렬, 60초 갱신
11. **설정** — TTS/MySQL/환경

## 시장·타임프레임·프리셋
- 시장: **CRYPTO(업비트 KRW)**, **US(S&P500 Top100 / NASDAQ100)**, **KOSPI(KOSPI100)**, **KOSDAQ(KOSDAQ150)**
- 코인 봉: 주/3일/일/4h/1h/15m · 주식 봉: 주/3일/일
- ABC 파라미터(config.py): `MIN_A_DROP_PCT=0.20, MIN_B_RETRACE=[0.236,0.886], LOCAL_WIN=5, SEARCH_WINDOW=100, MIN_0_PROMINENCE=0.10, STRICT_WAVE=True, MAX_PATTERNS=3`
- 캔들 수: 주봉 150 / 3일봉용 450 / 4h 200
- 자동 재스캔: 주·3일·일 4시간마다 / 4h·1h·15m 각 주기마다
- 데이터 소스: Upbit REST·WS, Binance REST/WS, CoinGecko(/global, coins/markets), DefiLlama, yfinance(미국·지수), pykrx(KR), Bybit(펀딩), Telethon(텔레그램), Bloomberg RSS, 환율(open.er-api.com)

## 모바일 적응 시 주의 (데스크톱→모바일 갭)
| 레거시 기능 | 모바일 처리 |
|---|---|
| 1/2/4 분할 멀티뷰 차트 | 모바일은 단일 차트 + 봉/심볼 전환. 분할 제외 |
| 텔레그램 Telethon 로그인 | 모바일 직접 로그인 부담 → 서버가 수집/중계, 앱은 읽기 |
| Windows TTS 음성알림 | 모바일 푸시/인앱 알림으로 대체 |
| 실시간 WS 스캘핑(틱띄기) | 서버 WS 수집 → 앱은 점수 랭킹 폴링. 부하 큼, 후순위 |
| MySQL 직접 저장 | PostgreSQL 서버 도메인으로 대체 |
| 자동스캔(클라 주기) | 서버 스케줄러(봉 확정 직후) 스캔 |

## 제안 모바일 IA (하단탭 5 + 스택)
- **홈/터미널**: 시장지표 카드(공포탐욕·도미넌스·김프) + 통합검색 + 관심(즐겨찾기) + 차트 진입
- **스캐너**: 패턴 4종(ABC·고점판독·이말올·삼각수렴) 통합 — 시장/패턴/타임프레임 필터 + 결과카드 + 상세 오버레이차트 + 틱띄기(후순위)
- **속보**: 텔레그램+Bloomberg 피드(서버 중계)
- **데이터**: TVL·유통량·테마/섹터·펀비차익
- **설정**: 알림·데이터출처·진단

종목 상세/차트, 신호 상세는 스택 화면. 차트 오버레이는 evidence(0/A/B 피벗, 삼각 H/L 추세선, C예상가 priceLine) 기준 (lightweight-charts / fl_chart).
