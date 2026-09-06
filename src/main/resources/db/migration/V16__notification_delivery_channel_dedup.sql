CREATE UNIQUE INDEX ux_delivery_attempt_notification_channel
  ON delivery_attempts(notification_id, channel);
