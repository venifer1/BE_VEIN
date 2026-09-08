# VEIN Round 44

Date: 2026-09-08

## ABC 탐지기 버그 수정 — 0→A 하락 레그 바닥 가드 (R43 발견 이슈)

R43 전체 테스트 실행에서 드러난 `AbcDetectorTest.allowsShortABWhenBIsHighestAfterA()`
선행 실패를 조사해 **탐지기 버그**로 확정하고 수정했다(테스트가 옳았다).

### 원인

입력: highs `{100,105,130,110,92,100,110,105,108,107}`, lows `{99,100,125,100,90,95,100,100,103,102}`,
params `localWin=1, strictWave=true`.

- 피크: idx2(130)·6(110)·8(108). 트로프: idx4(90)·**idx7(100)**.
- idx7이 트로프인 이유: `findTroughs`가 `<=`로 평탄부를 인정 → `lows[7]=100 ≤ lows[6]=100`·`≤ lows[8]=103`.
- 그 결과 후보가 둘: 의도한 **(0=2, A=4, B=6)** 와 얕은 **(0=2, A=7, B=8)**.
- dedup은 "같은 0값에서 **가장 최근 B**"를 남긴다 → idxB=8인 (2,7,8)이 (2,4,6)을 밀어냄
  → 반환 idxA=7 (기대 4)로 실패.

**핵심 결함:** A=idx7 바로 앞 idx4에 더 낮은 저점(90)이 있는데도 A로 채택됐다. 0→A 하락
레그는 A에서 바닥이어야 하는데 그 가드가 없었다(반면 A→B 레그엔 `breaksBelow` 대칭 가드가
이미 있었다). 얕은 2차 저점이 A로 뽑히면 기하학적으로 잘못된 파동이 만들어진다.

### 수정 (`AbcDetector`)

- STRICT_WAVE일 때 A 선정에 **`breaksBelow(lows, zero, a, aVal)` 가드 추가** — 0과 A 사이에
  A보다 낮은 저점이 있으면 그 A를 버린다. 기존 `breaksAbove`(0 미돌파)와 한 쌍으로 묶음.
- `ALGORITHM_VERSION` `abc-java-1.0.1` → `abc-java-1.0.2`(로직 변경 반영).
- 클래스 Javadoc의 A 규칙 갱신.

검증 결과 (2,7,8) 후보가 제거되고 (2,4,6)만 남아 idxA=4·idxB=6. 자매 테스트
`rejectsBWhenLaterHighAfterAExceedsIt`(0개 기대)와 골든(0개 기대)은 후보가 줄기만 하므로 영향 없음.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공.
- **ASCII 경로 복사본 전체 테스트 `BUILD SUCCESSFUL`** — 이전 37중 1 실패 → **전부 통과**.
  `com.vein.pattern.*`(ABC 단위 2 + 골든) 포함 그린.

### 영향

- 실데이터 재탐지 시 얕은 2차 저점을 A로 잡던 잘못된 ABC 후보가 사라진다(신호 품질↑).
  `algorithm_version`이 1.0.2로 찍히므로 이전/이후 신호 구분 가능. FE 변경 없음.
- (참고) `frontend/lib/mockData.ts`의 표시용 `"abc-java-1.0.0"`은 목업 샘플 문자열로 무해 —
  실계약/검증에 영향 없어 이번엔 미변경.
