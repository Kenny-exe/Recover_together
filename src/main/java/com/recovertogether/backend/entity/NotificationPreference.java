package com.recovertogether.backend.entity;

import com.recovertogether.backend.enums.NotificationType;
import jakarta.persistence.*;

/**
 * Per-type push preference for one user. A missing row means "enabled", so
 * defaults never need to be persisted and remain forward-compatible when a
 * new {@link NotificationType} is added.
 *
 * <p>SOS alerts are safety-critical and ignore this flag entirely; see
 * {@code NotificationPreferenceService#shouldPush}.</p>
 */
@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_notification_preferences_user_type",
                        columnNames = {"user_id", "notification_type"})
        },
        indexes = {
                @Index(name = "idx_notification_preferences_user", columnList = "user_id")
        })
public class NotificationPreference
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    public NotificationPreference()
    {
    }

    public NotificationPreference(User user, NotificationType type, boolean pushEnabled)
    {
        this.user = user;
        this.type = type;
        this.pushEnabled = pushEnabled;
    }

    public Long getId() {return id;}

    public void setId(Long id) {this.id = id;}

    public User getUser() {return user;}

    public void setUser(User user) {this.user = user;}

    public NotificationType getType() {return type;}

    public void setType(NotificationType type) {this.type = type;}

    public boolean isPushEnabled() {return pushEnabled;}

    public void setPushEnabled(boolean pushEnabled) {this.pushEnabled = pushEnabled;}
}
