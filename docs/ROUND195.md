# VEIN Round 195

Date: 2026-09-13

## 데모 알림 다이제스트(buildNotificationDigest) 분류/집계 단위 테스트 (FE, 회귀 보호)

자율 루프 R195. 데모 어댑터가 알림 다이제스트(R45)를 흉내내는 `buildNotificationDigest`
(카테고리 분류·시간창 필터·안읽음 집계)는 무테스트였다.

### 바뀐 것

- `lib/mockAdapter.ts`: `buildNotificationDigest` export(순수, 로직 무변경).
- `lib/mockAdapter.test.ts` +3:
  - 분류: signal_id→SIGNAL, "Scanner match:"→SCANNER, "Liquidation spike"→LIQUIDATION,
    그 외→SYSTEM. 안읽음(read_at null && status≠READ) 카테고리별 집계.
  - **시간창 필터**: 창(1h) 밖(5시간 전) 알림 제외.
  - 빈 입력 → total/unread 0·categories [].

### 검증

FE `npx vitest run` **8파일 129개 그린**(R194 126 → +3, mockAdapter 21).
