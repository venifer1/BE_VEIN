-- R42 잔여 스키마 드리프트 수정(R45에서 클린 DB 부팅 중 발견).
-- V24는 notification_prefs의 시각 컬럼을 SMALLINT(int2)로 만들었으나, JPA 엔티티는
-- `int quietStartHour/quietEndHour`(→ Hibernate INTEGER/int4)로 매핑한다. ddl-auto=validate가
-- int2 ≠ int4로 거부해 신규(클린) DB에서 SessionFactory 생성이 실패했다.
-- 시각(0-23)을 INTEGER로 넓혀 엔티티와 일치시킨다(기존 값은 그대로 보존).
ALTER TABLE notification_prefs
  ALTER COLUMN quiet_start_hour TYPE INTEGER,
  ALTER COLUMN quiet_end_hour   TYPE INTEGER;
