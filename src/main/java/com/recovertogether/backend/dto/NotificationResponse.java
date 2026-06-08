package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.Notification;
import com.recovertogether.backend.enums.NotificationType;
import java.time.LocalDateTime;

public class NotificationResponse
{
    private Long id;
    private NotificationType type;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;

    public NotificationResponse(Notification notification)
    {
        this.id = notification.getId();
        this.type = notification.getType();
        this.message = notification.getMessage();
        this.read = notification.isRead();
        this.createdAt = notification.getCreatedAt();
    }

    public Long getId() { return id; }

    public NotificationType getType() { return type; }

    public String getMessage() { return message; }

    public boolean isRead() { return read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
