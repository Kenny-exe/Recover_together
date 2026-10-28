package com.recovertogether.backend.entity;

import com.recovertogether.backend.enums.DevicePlatform;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A mobile device registered by the authenticated user, keyed by its push
 * notification token (FCM). Tokens are unique across installations: when a
 * token is re-registered by a different user, the previous binding is
 * replaced (device reinstall means the old registration is stale).
 */
@Entity
@Table(name = "user_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_devices_token", columnNames = "token")
        },
        indexes = {
                @Index(name = "idx_user_devices_user", columnList = "user_id")
        })
public class UserDevice
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(nullable = false, length = 4096)
    private String token;

    @Column(length = 100)
    private String deviceName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist()
    {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate()
    {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {return id;}

    public void setId(Long id) {this.id = id;}

    public User getUser() {return user;}

    public void setUser(User user) {this.user = user;}

    public DevicePlatform getPlatform() {return platform;}

    public void setPlatform(DevicePlatform platform) {this.platform = platform;}

    public String getToken() {return token;}

    public void setToken(String token) {this.token = token;}

    public String getDeviceName() {return deviceName;}

    public void setDeviceName(String deviceName) {this.deviceName = deviceName;}

    public boolean isActive() {return active;}

    public void setActive(boolean active) {this.active = active;}

    public LocalDateTime getCreatedAt() {return createdAt;}

    public void setCreatedAt(LocalDateTime createdAt) {this.createdAt = createdAt;}

    public LocalDateTime getUpdatedAt() {return updatedAt;}

    public void setUpdatedAt(LocalDateTime updatedAt) {this.updatedAt = updatedAt;}
}
