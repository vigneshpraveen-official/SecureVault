package com.securevault.notification;

import com.securevault.notification.dto.NotificationResponse;
import java.util.List;

public interface NotificationService {

    void create(Long userId, NotificationType type, String title, String message);

    List<NotificationResponse> list(Long userId);

    void markRead(Long notificationId, Long userId);

    void markAllRead(Long userId);
}
