package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.NotificationResponse;
import com.recovertogether.backend.dto.UnreadCountResponse;
import com.recovertogether.backend.entity.Notification;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.recovertogether.backend.entity.Notification;
import com.recovertogether.backend.enums.NotificationType;
import java.util.List;

@Service
public class NotificationService
{
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository)
    {
        this.notificationRepository=notificationRepository;
    }

    public List<NotificationResponse> getNotifications()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return notificationRepository.findByReceiverOrderByCreatedAtDesc(currentUser).stream().map(NotificationResponse::new).toList();
    }

    public UnreadCountResponse getUnreadCount()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        long count=notificationRepository.countByReceiverAndReadFalse(currentUser);
        return new UnreadCountResponse(count);
    }

    public void markAsRead(Long notificationId)
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Notification notification=notificationRepository.findByIdAndReceiver(notificationId, currentUser).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Notifications not found"));

        if(!notification.isRead())
        {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    public void createNotification(User receiver, NotificationType type, String message)
    {
        Notification notification=new Notification();

        notification.setReceiver(receiver);
        notification.setType(type);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }
}
