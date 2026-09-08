# VEIN Round 40

Date: 2026-09-08

## 데이터 출처 가시화 (실데이터 vs 합성 스텁) + 사이드카 재현성 마감 (Track A #2 · B #3)

PROJECT_STATUS §6가 **최우선 함정**으로 지목한 것 — "사이드카가 죽으면 에러 없이 합성
스텁으로 폴백한다 → 가짜 데이터를 진짜로 착각할 수 있음" — 을 눈에 보이게 만든다. 기존
`/system/status`는 provider별 *freshness*(FRESH/DELAYED)만 줬는데, 스텁도 타임스탬프는
최신이라 freshness만으로는 실/가짜를 구분할 수 없었다.

### 추가된 것

**백엔드 (`com.vein.ops`)**
- `SidecarHealth` — `EquitySidecarClient.isHealthy()`(HTTP 왕복)를 **30s TTL 캐시**로 감싼
  컴포넌트. 자주 폴링되는 `/system/status`를 싸게 유지. never throw → 실패 시 unhealthy.
- `/system/status` 확장:
  - `ProviderStatus`에 **`source`(REAL|STUB)** 추가. 사이드카 경유(yfinance·pykrx·telegram)는
    `sidecar.healthy=false`면 STUB, keyless 직결(upbit·binance·coingecko·defillama·bybit·
    bloomberg·binance_futures)은 항상 REAL.
  - `SystemStatusDto`에 **`sidecar{healthy,url}`** 블록 추가.

**프론트 (FE_VEIN)**
- 설정 "데이터 출처 · 시스템 상태": provider마다 **실데이터/합성 배지**(`SourceBadge`),
  `sidecar.healthy=false`면 상단에 **⚠ 사이드카 다운 — …합성 스텁** 경고 배너.
- `types.ts`(ProviderStatus.source, SidecarStatus, SystemStatus.sidecar), mock parity
  (목업은 사이드카 없음 → yfinance/pykrx/telegram=STUB, sidecar.healthy=false).

**사이드카 재현성 마감 (Track A #2)**
- `README.md`를 **standalone venv 우선**으로 개정(`python -m venv .venv && pip install -r
  requirements.txt`). 레거시 venv(`C:\veni_invest_abc\.venv`)는 "선택·단축"으로 강등.
  이로써 SIDECAR_VEIN 저장소만 클론해도 재현 가능(기존 requirements.txt·gitignore는 이미 존재).
- PROJECT_STATUS §2.1 사이드카 행 정정(레거시 저장소 → SIDECAR_VEIN 분리), §4 Track A #2 ✅,
  §6 함정 항목에 R40 가시화 반영.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- 프론트 `tsc --noEmit` 0에러, 프로덕션 `build` 성공.
- **라이브 Chrome smoke(mock 빌드) 전 라우트 0에러.** 설정 화면에 사이드카 다운 경고 배너 +
  yfinance/pykrx/telegram "합성" 배지, upbit/binance 등 "실데이터" 배지 렌더 — 스크린샷 확인.
- 검증 후 실데이터 설정으로 재빌드해 운영 상태 복원.

### 남은 것 / 주의

- 실 백엔드에서 STUB 판정은 `SidecarHealth`(사이드카 `/health`)에 의존 — 사이드카가 떠 있지만
  특정 심볼만 실패하는 경우까지는 구분 못 함(전체 up/down 수준). 심볼 단위 스텁 표시는 후속.
- 사이드카 **배포 자동화**는 아직 수동(스켈레톤은 SIDECAR_VEIN에 존재). Track A #2의 "재현"은
  해소, "배포"는 후속.
