package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.NotificationResponse;
import com.recovertogether.backend.dto.UnreadCountResponse;
import com.recovertogether.backend.entity.Notification;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.repository.NotificationRepository;
import org.springframework.transaction.annotation.Transactional;import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class NotificationService
{
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;
    private final RealtimeEventService realtimeEventService;

    public NotificationService(NotificationRepository notificationRepository,
                               PushNotificationService pushNotificationService,
                               RealtimeEventService realtimeEventService)
    {
        this.notificationRepository = notificationRepository;
        this.pushNotificationService = pushNotificationService;
        this.realtimeEventService = realtimeEventService;
    }

    private static final int MAX_LIMIT = 100;

    public List<NotificationResponse> getNotifications(int limit, Long beforeId)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (limit < 1)
        {limit = 1;}

        else if (limit > MAX_LIMIT)
        {limit = MAX_LIMIT;}

        List<Notification> notifications;
        if (beforeId != null)
        {
            notifications = notificationRepository.findByReceiverAndIdLessThanOrderByCreatedAtDesc(currentUser, beforeId);
        }
        else
        {
            notifications = notificationRepository.findByReceiverOrderByCreatedAtDesc(currentUser);
        }

        return notifications.stream().limit(limit).map(NotificationResponse::new).toList();
    }

    public List<NotificationResponse> getNotifications(int limit)
    {
        return getNotifications(limit, null);
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
        createNotification(receiver, type, message, java.util.Map.of());
    }

    public void createNotification(User receiver, NotificationType type, String message,
                                   java.util.Map<String, String> extraData)
    {
        Notification notification=new Notification();

        notification.setReceiver(receiver);
        notification.setType(type);
        notification.setMessage(message);
        notificationRepository.save(notification);

        java.util.Map<String, String> data = new java.util.HashMap<>();
        if (type != null)
        {
            data.put("type", type.name());
        }
        data.put("screen", screenFor(type));
        if (extraData != null)
        {
            data.putAll(extraData);
        }

        // Best-effort push to the receiver's registered devices; the push
        // service swallows all failures so this never blocks the workflow.
        try
        {
            pushNotificationService.push(receiver, titleFor(type), message, data);
        }
        catch (Exception e)
        {
            // Never break the caller's transaction because of push.
        }

        // Foreground live update for the mobile SSE stream.
        try
        {
            realtimeEventService.publish(receiver.getId(), "notification", new NotificationResponse(notification));
        }
        catch (Exception e)
        {
            // Realtime delivery is best-effort as well.
        }
    }

    /** Mobile screen route shipped in the push payload for tap navigation. */
    public static String screenFor(NotificationType type)
    {
        if (type == null)
        {
            return "home";
        }
        return switch (type)
        {
            case NEW_MESSAGE -> "chat";
            case SOS_ALERT -> "sos";
            case PARTNER_RELAPSE -> "partner";
            case MISSED_CHECKIN -> "checkin";
            case ACHIEVEMENT_UNLOCKED, PARTNER_ACHIEVEMENT -> "achievements";
            case DAILY_CHECKIN_REMINDER -> "checkin";
            case SUPPORT_REQUEST -> "support";
        };
    }

    public static String titleFor(NotificationType type)
    {
        if (type == null)
        {
            return "RecoverTogether";
        }
        return switch (type)
        {
            case NEW_MESSAGE -> "New message";
            case SOS_ALERT -> "SOS alert";
            case PARTNER_RELAPSE -> "Partner update";
            case MISSED_CHECKIN -> "Missed check-in";
            case ACHIEVEMENT_UNLOCKED -> "Achievement unlocked";
            case PARTNER_ACHIEVEMENT -> "Partner achievement";
            case DAILY_CHECKIN_REMINDER -> "Check-in reminder";
            case SUPPORT_REQUEST -> "Support request";
        };
    }

    @Transactional
    public void deleteNotification(Long id)
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        long deleted=notificationRepository.deleteByIdAndReceiver(id,currentUser);

        if(deleted==0)
        {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found");
        }
    }

    @Transactional
    public void markAllAsRead()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<Notification> notifications=notificationRepository.findByReceiverAndReadFalse(currentUser);

        for(Notification notification: notifications)
        {
            notification.setRead(true);
        }
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void deleteAllNotifications()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        notificationRepository.deleteByReceiver(currentUser);
    }
}
