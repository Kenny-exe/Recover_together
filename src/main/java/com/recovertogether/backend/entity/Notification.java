package com.recovertogether.backend.entity;

import com.recovertogether.backend.enums.NotificationType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private boolean read = false;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist()
    {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public User getReceiver() { return receiver; }

    public void setReceiver(User receiver) {this.receiver = receiver;}

    public NotificationType getType() {return type;}

    public void setType(NotificationType type) {this.type = type;}

    public String getMessage() {return message;}

    public void setMessage(String message) {this.message = message;}

    public boolean isRead() {return read;}

    public void setRead(boolean read) {this.read = read;}

    public LocalDateTime getCreatedAt() {return createdAt;}

    public void setCreatedAt(LocalDateTime createdAt) {this.createdAt = createdAt;}
}
