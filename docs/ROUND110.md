# VEIN Round 110

Date: 2026-09-13

## CursorUtil 라운드트립·불량 커서 단위 테스트 (BE, 회귀 보호)

자율 루프 R110. 커서 페이지네이션(`CursorUtil`)은 신호·캔들·뉴스·알림 등 다수 목록에서
쓰이는데 테스트가 없었다. 잘못된 커서가 500이 아니라 `INVALID_CURSOR` 400으로 정규화되는지
포함해 회귀 보호를 추가.

### 바뀐 것

- `src/test/java/com/vein/common/CursorUtilTest.java` (5 케이스):
  - encode→decode 라운드트립이 ts(밀리)·id 보존.
  - encode는 URL-safe base64(패딩·`+`·`/` 없음).
  - garbage/파이프 없는 세그먼트/비수치 파트 → `ApiException(INVALID_CURSOR)`(500 아님).

### 검증

ASCII 경로 복사본에서 `gradlew test --tests CursorUtilTest` **그린**. 코드 무변경(테스트만).
</content>
