package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.Notification;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>
{
    List<Notification> findByReceiverOrderByCreatedAtDesc(User receiver);
    long countByReceiverAndReadFalse(User receiver);
    Optional<Notification> findByIdAndReceiver(Long id, User receiver);
    boolean existsByReceiverAndTypeAndMessage(User receiver, NotificationType type, String message);
    long deleteByIdAndReceiver(Long id, User receiver);
    List<Notification> findByReceiverAndReadFalse(User receiver);

}
