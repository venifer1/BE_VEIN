# VEIN Round 47

Date: 2026-09-12

## 차트 조작감 개선 (Track A #3 마지막 항목)

프론트 전용 UX 라운드. `components/chart-view.tsx`(lightweight-charts)가 팬/줌·과거 캔들
로드·리사이즈마다 **차트를 통째로 재생성**하던 근본 문제와, hover 시 값을 읽을 수 없던
공백을 잡는다. 백엔드/계약 변화 없음(ChartView props 시그니처 그대로).

### 문제 (재생성 유발)

기존은 단일 거대 `useEffect`가 `candles·evidence·invalidation·indicators·overlayData·height·
onLoadOlder·hasOlder·isLoadingOlder` 전부에 의존해, 그중 하나만 바뀌어도 `chart.remove()` 후
새로 만들었다. 그래서:

- 과거 캔들 무한스크롤(`isFetchingNextPage`·`candles` 변경)마다 차트가 깜빡이고 크로스헤어·
  줌 상태가 초기화됐다.
- `onLoadOlder`(부모가 `useCallback`으로 memo했어도)와 페이지네이션 플래그가 deps에 있어
  로딩 상태 전환만으로도 재생성됐다.
- 리사이즈(ResizeObserver → 부모 리렌더)나 오버레이 토글도 전체 재생성으로 번졌다.

### 바뀐 것 (`components/chart-view.tsx`)

- **마운트 1회 생성**: `createChart` + 캔들 시리즈 + ResizeObserver + 크로스헤어/가시범위 구독을
  deps `[]` effect에서 한 번만. 언마운트에서만 정리.
- **부분 갱신 effect 분리**: 캔들 데이터, (마커+증거선+가격선), 클라이언트 오버레이(MA/BB),
  지표 평선, height를 각각 자기 deps에서만 갱신. 동적으로 추가한 라인 시리즈·가격선은 ref
  배열로 추적해 다음 갱신 때 **정확히 추가분만 제거**(전체 teardown 없음).
- **콜백 ref화**: `onLoadOlder/hasOlder/isLoadingOlder`를 `loadCbRef`에 담아 안정적인 가시범위
  핸들러가 읽는다 → 페이지네이션 상태가 바뀌어도 재구독/재생성 없음.
- **과거 로드 뷰 유지**: 캔들 앞에 오래된 봉이 prepend되면 논리범위를 그만큼 시프트해 화면을
  고정(기존 로직 보존, 이제 teardown 없이 동작).
- **크로스헤어 OHLC 레전드**: `subscribeCrosshairMove`로 좌상단에 `날짜 · 시/고/저/종 · 등락%`를
  표시(등락 색상). hover를 벗어나면 최신봉으로 폴백. 값 포맷은 `formatPrice` 재사용, `tabular-nums`.
- **모바일 우선 조작감 튜닝**: `handleScroll{horzTouchDrag:true, vertTouchDrag:false}`로 차트 위에서도
  페이지 세로 스크롤이 살아있게, `handleScale{pinch:true}` 핀치 줌, `kineticScroll{touch:true}`
  관성 스크롤, `timeScale.rightOffset` 여백.
- 빈 상태는 컨테이너를 항상 렌더한 뒤 오버레이로 표시(마운트-1회 생성 모델과 호환).

### 검증

- 프론트 `typecheck` / 프로덕션 `build` 통과. `/signals/[id]`·`/instruments/[id]` 번들 정상.
- **라이브 Chrome smoke 0에러** — 스캐너 첫 신호를 따라가 `/signals/[id]` 차트를 렌더하는 기존
  경로가 그대로 통과(팬/렌더 중 콘솔·페이지 에러 없음).
- 신호 상세 차트 스크린샷 육안 확인: 크로스헤어 레전드(기본 최신봉 `시/고/저/종 · +0.00%`),
  볼린저 오버레이·매치 마커·C목표가/무효화 가격선·캔들 모두 정상 렌더.

### 참고

- 순수 UX/렌더 라운드라 단위 테스트 대상이 아님(집계·경계 로직 없음). 계약·백엔드 무변경이라
  백엔드 회귀 없음.
- 이걸로 Track A #3(실사용 마찰) 목록 — 신호 과다(R41)·알림 노이즈(R42/R45/R46)·차트 조작감(R47) —
  이 모두 마감. Track A 사실상 종료. 다음은 Track B(온보딩·엣지케이스).
