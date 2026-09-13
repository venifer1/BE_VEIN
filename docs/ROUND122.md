# VEIN Round 122

Date: 2026-09-13

## 프론트 단위 테스트 도입(vitest) + 순수 헬퍼 커버리지 (FE, 회귀 보호)

자율 루프 R122. FE는 14k줄인데 **단위 테스트가 0**이었다(smoke.mjs는 e2e). 표시·경로
헬퍼가 회귀해도 잡히지 않던 갭을, dev 전용 vitest 도입 + 순수 헬퍼 테스트로 메꿨다.

### 바뀐 것 (프론트, dev 전용)

- `vitest@^1.6`(devDependency, @types/node 20과 호환되는 라인 — v5는 node22+ 요구라 충돌).
  `package.json` scripts에 `"test": "vitest run"`.
- `lib/format.test.ts` (9): formatPct(+/-/0·null·garbage), pctSign, formatScore(1자리·null),
  formatPrice(≥1000 콤마·sub-1·null·passthrough), formatRelative(초/분/시간/일 전·null).
- `lib/types.test.ts` (5): stripIdPrefix(sig_/ins_ 제거·숫자·null), signalPathId/instrumentPathId
  동일 스트리퍼, timeframesForMarket(코인=풀셋·주식=일/3일/주).

### 검증

`vitest run` **14/14 그린**. `tsc --noEmit`·`next build` 통과(테스트 파일은 빌드 무영향,
vitest는 dev 전용이라 프로덕션 번들 미포함). BE 계약과 동일하게 (시장,봉) 세트를 FE에서도 고정.
</content>
