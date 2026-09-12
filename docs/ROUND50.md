# VEIN Round 50

Date: 2026-09-12

## 데이터 출처 사용지점 노출 (Track B #3 마저 다듬기)

프론트 전용. R40이 provider별 `source`(REAL/STUB)를 `/system/status`에 실었지만 **설정 화면 한
곳에만** 노출했다. 합성 스텁 데이터(사이드카 다운 시 미국·한국 주식·텔레그램)는 종목 차트·데이터
탭 등 여러 화면에 조용히 뜨는데, 사용자는 그 화면을 보는 동안 가짜인 줄 모른다. R50은 기존
`/system/status`를 **사용 지점에서 읽어** 경고를 붙인다. 백엔드/계약 변화 없음.

### 추가 (`frontend`)

- `components/data-source.tsx`:
  - `DataSourceBanner` — 전역 얇은 배너. 사이드카 다운 또는 STUB 프로바이더가 하나라도 있으면
    앱 상단(sticky 헤더 아래)에 상시 노출: "⚠ 일부 데이터가 합성값입니다 · 미국주식·국내주식·
    텔레그램 · 자세히", 설정 "데이터 출처"로 링크. 정상(전부 REAL)이면 렌더 안 함.
  - `MarketStubBadge({market})` — 시장이 명확한 자리에서 그 시장이 합성이면 붙는 "합성" 배지.
  - `providerForMarket`/`isMarketStub` — 시장→프로바이더(US→yfinance, KOSPI/KOSDAQ→pykrx,
    코인=실연동 null) 매핑 + STUB 판정.
- `AppShell`: 헤더+배너를 하나의 sticky 컨테이너로 묶어 전 화면 상단에 배너 노출(인증 영역에서만
  마운트되므로 `/system/status`도 그때만 조회).
- 종목 상세(`/instruments/[id]`) 헤더의 `MarketBadge` 옆에 `MarketStubBadge` 추가 → 미국·국내
  종목 차트를 보는 자리에서 "합성"이 바로 보인다.

### 검증

- 프론트 `typecheck`/프로덕션 `build` 통과.
- **라이브**(사이드카 미기동 = yfinance/pykrx/telegram STUB, sidecar.healthy=false):
  - `/system/status` 실측: sidecar down, 3개 프로바이더 STUB 확인.
  - 전역 배너가 홈 등 인증 화면 상단에 노출(스크린샷 육안). **라이브 Chrome smoke 0에러**(신규
    `data-source-banner` 체크 = 홈에 "일부 데이터가 합성값입니다" 노출 확인).
  - 종목 상세: `US`(AAPL)·`KOSPI`(삼성전자) 모두 헤더에 "합성" 배지 노출(스크린샷 육안).
  - 검증 후 임시 유저 정리로 DB 원복.

### 메모

- 순수 FE(기존 엔드포인트 읽기)라 백엔드·계약 무변경, mock도 기존 `/system/status`(sidecar
  healthy=false + STUB)로 동일하게 배너가 뜬다.
- 이걸로 Track B #3(데이터 출처/지연 표시)까지 정리. 남은 Track B: #1 Flutter(사용자 지시로 보류).
  실질적으로 Track A·B의 실사용 항목이 대체로 마감됨.
