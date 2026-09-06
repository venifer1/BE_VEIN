package com.vein.notification;

import com.vein.common.TimeUtil;

/**
 * Notification representation returned to clients (snake_case via global Jackson config).
 */
public record NotificationDto(String id, String status, String title, String body, Long signalId,
                              String createdAt, String readAt) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                String.valueOf(n.getId()),
                n.getStatus().name(),
                n.getTitle(),
                n.getBody(),
                n.getSignalId(),
                TimeUtil.toIso(n.getCreatedAt()),
                TimeUtil.toIso(n.getReadAt()));
    }
}
