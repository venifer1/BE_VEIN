# VEIN Round 64

Date: 2026-09-12

## 모의 계정 리셋 UI (R31 계획 항목)

자율 루프 R64. 먼저 모의투자 주문 엣지를 실측(올바른 DTO: `side` BUY/SELL, `type` MARKET/LIMIT,
`position_side` LONG/SHORT)해 **전부 견고**함을 확인: 잔액부족→400 "Insufficient paper cash",
보유초과 매도→400, 레버리지>50x→400, 0→400, 정상 주문→201. 500 없음.

버그는 없었고, 대신 **계정 리셋 UI 부재**(R31 계획·R55 확인)를 채웠다. `POST /paper/accounts`는
create-or-reset(재호출 시 포지션·주문·성과 초기화)인데 계정이 있으면 FE에 리셋 경로가 없었다.

### 추가 (FE)

- 모의투자 계정 화면 하단에 **"계정 리셋"**(접힘) → 잔액 입력(천단위 콤마+한글 단위) → confirm →
  `useCreatePaperAccount`로 재생성(초기화). 성공 시 portfolio/performance 자동 갱신(기존 무효화).
  파괴적이라 접힌 링크 + confirm 2단계.

### 검증

- 실측: 모의 주문 엣지 전부 400/201(500 없음), 리셋 동작 확인(현금 9.9M/1포지션 → 5M/0).
- 프론트 `typecheck`/`build` 통과. 리셋 컨트롤 렌더(계정 존재 시) DOM 확인. **Chrome smoke 0에러**.
  백엔드/계약 무변경(기존 create-or-reset 재사용).
