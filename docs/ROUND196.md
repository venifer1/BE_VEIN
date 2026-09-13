# VEIN Round 196

Date: 2026-09-13

## 데모 경제 캘린더(upcomingMacroEvents) 창/디데이 단위 테스트 (FE, 회귀 보호)

자율 루프 R196. 데모 어댑터의 경제 캘린더(R39) `upcomingMacroEvents`(시드 이벤트를 창
일수로 필터, "오늘"부터 상대 배치)는 무테스트였다.

### 바뀐 것

- `lib/mockAdapter.ts`: `upcomingMacroEvents` export(로직 무변경).
- `lib/mockAdapter.test.ts` +3:
  - 창 필터: days=1 → [1], days=10 → [1,4,9](시드 inDays).
  - **창 클램프 [1,90]**: 0 → 1일 창, 1000 → 90일 창(전 시드 포함).
  - date = 오늘+dday(UTC yyyy-MM-dd), region "US".

### 검증

FE `npx vitest run` **8파일 132개 그린**(R195 129 → +3, mockAdapter 24).
