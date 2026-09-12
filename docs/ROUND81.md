# VEIN Round 81

Date: 2026-09-12

## 홈 "오늘의 주목 신호" 종목별 dedup (BE, 큐레이션 품질)

자율 루프 R81. 실화면(smoke 스크린샷) 점검에서 홈 "오늘의 주목 신호"(`GET /signals/top`, R41)에
**같은 종목이 중복 노출**되는 것을 발견했다. 실측: `top?limit=10` = WBA·SPLK·SGEN·ANSS·MMC가
각각 **타임프레임만 달리해(1w·3d) 두 번씩**(전부 IMALOL·score 100). 기본 6칸이면 WBA가 중복돼
서로 다른 후보는 5개뿐이었다. "시스템이 다양한 후보를 먼저 올려준다"는 핵심 가치가 깎인다.

### 바뀐 것

- `SignalService.top()` — 종목별 최상위 1건만 남긴다. 쿼리(`findTopByScore`)가 이미
  `score desc, detectedAt desc` 정렬이라 **종목 첫 등장이 최상위**. 넉넉히(`size×5`, ≤100) 받아
  순수 함수 `dedupeByInstrument(ordered, size)`(LinkedHashMap `putIfAbsent`)로 dedup 후 size로 자른다.
- 정렬 기준·활성 필터·계약(DTO) 무변경. FE 무변경(같은 응답 형태, 항목만 종목 유니크).

### 검증

- 단위: `SignalServiceTopTest` 3건(종목별 첫 등장 유지+limit / distinct<limit면 전부 / 빈입력)
  — **전체 그린**(ASCII 경로 `C:\vein_be`).
- 라이브(재기동 후): `top?limit=6` → **6종목 전부 유니크**(중복 0). 기존 WBA가 차지하던 6번째
  슬롯을 다른 후보(`005290.KQ` TOP, score 96.10)가 채워 시장·패턴 다양성도 늘었다.
- 프론트 무변경 → 라이브 Chrome full smoke **0에러**.

### 남긴 것 (백로그)

- `/signals/top`은 저장된 **구조 점수(detector score)**로 정렬한다(주석/문서의 "Pattern Score"는
  부정확). R21 종합 Pattern Score로 정렬하려면 신호마다 캔들·지표·뉴스·성과 조회가 필요해 홈이
  느려진다 → **탐지 시 pattern_score 영속화**(스키마 추가)가 선행돼야 하는 별도 큰 라운드 후보.
