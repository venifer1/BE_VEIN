-- 구독 티어(Track C 착수, R52). 수익화 토대 — FREE/PRO 2단계. 결제 연동은 후속(컴플라이언스
-- 리스크가 커 별도). 기존 사용자·신규 가입 모두 기본 FREE. 엔타이틀먼트(기능/한도)는 티어에서
-- 파생하므로 여기서는 티어만 저장한다.
ALTER TABLE users ADD COLUMN tier VARCHAR(16) NOT NULL DEFAULT 'FREE';
