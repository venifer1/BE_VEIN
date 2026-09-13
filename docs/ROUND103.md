# VEIN Round 103

Date: 2026-09-13

## mock `/signals/top` 종합 점수 정렬 파리티 (FE, R90 파리티)

자율 루프 R103. R90에서 백엔드 `/signals/top`을 종합 Pattern Score(`coalesce(pattern_score,
score)`)로 정렬했는데, mock 어댑터는 여전히 구조 `score`로 정렬(드리프트) — 오프라인 데모의
홈 주목신호 순서가 실서버와 달랐다.

### 바뀐 것 (프론트 mock 전용)

- `mockAdapter` `/signals/top`이 `pattern_score ?? score`(coalesce)로 정렬하도록 수정.
  백엔드 R90과 동일 순서. mockData 활성 신호엔 이미 pattern_score 있음(R101).

### 검증

`tsc`·`next build` 통과. mock 활성 신호 종합점수 76·71·69·64·58 기준 정렬 순서는
sig_001→003→005→004→009인데, 기존 구조점수(82.5·71.4·68.2·65.0·63.1) 정렬이면
001→004→003→005→009라 순서가 달라짐 — 이제 백엔드 coalesce 정렬과 일치.
</content>
