package com.recovertogether.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "achievements", uniqueConstraints =
@UniqueConstraint(columnNames = {"user_id","title"}), indexes =
        {@Index(name = "idx_achievement_user",columnList = "user_id")})
public class Achievement
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private LocalDateTime earnedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    public void prePersist()
    {
        earnedAt=LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getTitle() { return title; }

    public void setTitle(String title) {this.title = title;}

    public LocalDateTime getEarnedAt() {return earnedAt;}

    public User getUser() {return user;}

    public void setUser(User user) {this.user = user;}
}

