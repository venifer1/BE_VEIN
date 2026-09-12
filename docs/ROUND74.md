# VEIN Round 74

Date: 2026-09-12

## API_CONTRACT.md 동기화 (Track B 단일 계약 문서)

자율 루프 R74. `API_CONTRACT.md`는 "단일 진실 소스"인데, R45~R73 동안 추가된 엔드포인트가
문서에 누락돼 있었다. **문서화 vs 실제 엔드포인트 정적 대조**로 8개 그룹의 누락을 확인·반영했다.

### 반영한 누락(대조 결과)

- `/signals ?active_only`(R54) — 활성만 필터
- `/scanner/rules` PATCH·simulate·history + **FREE 3개 한도(402)**·빈바디 400(R52·R58)
- `DELETE /alerts/{id}`(R62) + **알림 FREE 10개 한도(402)**(R56)
- `POST /notifications/read-all`(R63) + `GET /notifications/digest`(R45·R51, released 포함)
- `/me/notification-prefs`에 스풀링(R46) 반영
- `GET /me/onboarding`·`POST /dismiss`(R48)
- **신규 §10 구독/운영**: `/me/entitlements`(R52·R60, tier·features·used), `PATCH /admin/users/{id}/tier`
  (R56), `/admin/overview by_tier`(R72), admin 사용자/감사/알림 목록

### 검증

- 문서 전용(코드/계약 무변경). 반영 후 재대조: 누락 8개 그룹 모두 문서에 존재함을 확인.
