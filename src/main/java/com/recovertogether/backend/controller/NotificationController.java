package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.MessageResponse;
import com.recovertogether.backend.dto.NotificationResponse;
import com.recovertogether.backend.dto.UnreadCountResponse;
import com.recovertogether.backend.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController
{
    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService)
    {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications()
    {
        return notificationService.getNotifications();
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount()
    {
        return notificationService.getUnreadCount();
    }

    @PutMapping("/read/{notificationId}")
    public MessageResponse markAsRead(
            @PathVariable Long notificationId)
    {
        notificationService.markAsRead(notificationId);

        return new MessageResponse(
                "Notification marked as read"
        );
    }
}