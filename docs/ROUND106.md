# VEIN Round 106

Date: 2026-09-13

## 조건검색 지표 라벨 용어 다듬기 + 누적 스모크 (FE)

자율 루프 R106. 조건검색 빌더 지표 드롭다운에 영문 잔재("MA5", "MACD Histogram")가 남아
있어 용어 스타일(R93/R95)에 맞춰 정리. 겸사겸사 R102~R105 누적 변경을 mock 스모크로 점검.

### 바뀐 것 (프론트 전용)

- `condition-scanner-panel` INDICATORS 라벨: `MA5` → **"이동평균5(MA5)"**(R95 차트 토글과 일치),
  `MACD Histogram` → **"MACD 히스토그램"**. RSI(14)·거래량/20평균·현재가는 유지.

### 검증

`tsc`·`next build` 통과. mock 빌드(3100) full smoke **0에러** — R102(상세 헤더 종합점수)·
R103(mock top 정렬)·R104(RSI 색)·R105(MACD 색)·R106 포함 전 화면 정상. 실데이터 빌드로 원복.
</content>
