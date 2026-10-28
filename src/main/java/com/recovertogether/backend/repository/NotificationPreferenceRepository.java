package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.NotificationPreference;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long>
{
    List<NotificationPreference> findByUser(User user);

    Optional<NotificationPreference> findByUserAndType(User user, NotificationType type);
}
