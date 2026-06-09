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
    public List<NotificationResponse> getNotifications(@RequestParam(defaultValue = "20")int limit)
    {
        return notificationService.getNotifications(limit);
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

    @DeleteMapping("/{id}")
    public MessageResponse deleteNotification(@PathVariable Long id)
    {
        notificationService.deleteNotification(id);
        return new MessageResponse("Notification deleted");
    }

    @PutMapping("/read-all")
    public  MessageResponse markAllAsRead()
    {
        notificationService.markAllAsRead();
        return new MessageResponse("All notifications marked as read");
    }

    @DeleteMapping("/all")
    public MessageResponse deleteAllNotifications()
    {
        notificationService.deleteAllNotifications();
        return new MessageResponse("All notifications deleted");
    }
}