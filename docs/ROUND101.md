# VEIN Round 101

Date: 2026-09-13

## 종합 Pattern Score를 신호 카드에 노출 (BE+FE, R90/R98 완결)

자율 루프 R101. R90이 홈 주목신호를 종합 Pattern Score로 정렬했지만 카드엔 구조 점수만
보여 순서가 뒤죽박죽처럼 보였고(R98에서 안내 문구로 임시 완화), 정작 정렬 기준인 종합
점수 숫자는 어디에도 없었다. 이번에 종합 점수를 리스트 DTO에 노출해 카드에 직접 표기.

### 바뀐 것

**백엔드**
- `SignalDto`에 `patternScore` 추가(score 뒤). `SignalService.toDto`가 `s.getPatternScore()`로
  채움(null-safe, 미계산 시 null). `/signals`·`/signals/top` 카드 응답에 포함.

**프론트**
- `Signal` 타입에 `pattern_score?`. `SignalCard`가 종합 점수 있으면 **"종합 {점수}"**, 없으면
  기존 "구조 점수 {점수}" 표기(홈 정렬 기준과 카드 숫자가 일치).
- `mockData` 활성 신호에 pattern_score 부여(구조와 다른 값)로 패리티.

**문서**
- API_CONTRACT `/signals` 카드 필드에 `pattern_score(nullable, R101)` 명시.

### 검증

- BE: compileJava·전체 test 그린(ASCII 경로). 라이브(Docker+bootRun) `/signals/top?limit=4`
  → `score=93.20/pattern_score=70.00` 등 노출 확인, **종합 점수 순 정렬**과 일치(구조 93.2
  신호가 종합 73 신호 아래). 임시 유저 정리.
- FE: tsc·build 통과, mock 빌드(3100) full smoke **0에러**, 홈 캡처로 카드 "종합 76.0/64.0/
  71.0/69.0/58.0" 렌더 확인(정렬 순서와 일치). 실데이터 빌드로 원복.
</content>
