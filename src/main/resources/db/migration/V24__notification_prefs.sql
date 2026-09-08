-- 조용한 시간(Quiet Hours) — 알림 노이즈 완화(R42, 실사용 마찰 제거).
-- 사용자별 1행. 시각은 KST 시(0-23). start==end면 창 없음, start>end면 자정을 넘는 창.
CREATE TABLE notification_prefs (
  user_id          BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  quiet_enabled    BOOLEAN  NOT NULL DEFAULT FALSE,
  quiet_start_hour SMALLINT NOT NULL DEFAULT 22,
  quiet_end_hour   SMALLINT NOT NULL DEFAULT 8,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
