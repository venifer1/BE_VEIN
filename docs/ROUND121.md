# VEIN Round 121

Date: 2026-09-13

## 라이브 견고성 프로브 — 잔여 500 없음 (BE, 검증)

자율 루프 R121. 폴리시 소진 후 더 높은 가치인 견고성 확인으로, 백엔드를 라이브로 띄워
읽기/쓰기 엔드포인트에 불량 입력 20종을 넣고 미처리 500이 있는지 훑었다(R43/R49/R58/R78/R79
하드닝 이후 첫 종합 프로브).

### 프로브 결과 (전부 비-500)

- **읽기 불량입력**: 나쁜 enum(`type=BOGUS`)→400, 비수치 path(`/signals/abc`)→400,
  없는 id→404, 나쁜 cursor(`@@@`)→400 INVALID_CURSOR, 나쁜 ISO(`from=notaniso`)→400,
  미지원 봉(`2h`)→400, 없는 심볼(scalp/deriv)→404. clamp성 입력(days=99999/-3, movers
  type/market bogus, indices history days=-5)→200(빈/폴백, 비-500).
- **쓰기 빈/불량 바디**(`{}`/`not-json`): scanner/run·paper/orders·alerts·backtests/run·
  strategies **전부 400**(R58/R79 하드닝 유효).
- **500 발생: 0건.**

### 결론

주요 읽기·쓰기 경로는 불량 입력에 견고(미처리 500 없음). 코드 무변경(검증 라운드).
검증 후 임시 유저 정리·백엔드/도커 중지.
</content>
