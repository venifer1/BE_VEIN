-- 알림 스풀링(R46, Track A #3 마무리). 조용한 시간(R42)에는 알림을 드롭하는 대신 보류한다.
-- held_until = 조용한 시간 창이 끝나는 순간(UTC). 이 시각 전까지는 읽기 모델(목록·안읽음·
-- 다이제스트)에서 제외돼 핑/배지가 뜨지 않고, 시각이 지나면 자연히 노출된다(lazy-release).
-- NULL = 보류 아님(즉시 활성, 기존 알림 전부 해당).
ALTER TABLE notifications ADD COLUMN held_until TIMESTAMPTZ NULL;

-- 창 종료 시 활성으로 전환될 알림을 빠르게 훑기 위한 부분 인덱스.
CREATE INDEX idx_notifications_held_until ON notifications (held_until) WHERE held_until IS NOT NULL;
