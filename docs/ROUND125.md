# VEIN Round 125

Date: 2026-09-13

## indicesByKey 단위 테스트 (FE, 회귀 보호)

자율 루프 R125. 홈 시장지표 섹션이 쓰는 `indicesByKey`(지수 배열→key 맵) 순수 헬퍼에
테스트를 추가.

### 바뀐 것 (프론트, 테스트만)

- `lib/types.test.ts`에 indicesByKey 3케이스: key로 매핑, null/undefined→빈 맵, 중복 key는
  마지막이 승리.

### 검증

`vitest run` **24/24 그린**(format 12·types 8·api 4). `tsc` 통과. 소스 무변경(테스트만).
</content>
