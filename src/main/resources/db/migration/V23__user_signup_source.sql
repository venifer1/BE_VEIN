-- 공개 베타 회원가입(MONETIZATION 단계2 ①·③).
-- 유입 추적: 어느 콘텐츠/리포트가 가입을 만들었는지 측정하기 위한 출처 컬럼.
-- 둘 다 NULL 허용 — 시드 계정·기존 사용자·유입정보 없는 가입을 위해.
ALTER TABLE users ADD COLUMN signup_source   VARCHAR(64);   -- utm_source 등
ALTER TABLE users ADD COLUMN signup_referrer VARCHAR(255);  -- document.referrer 등
