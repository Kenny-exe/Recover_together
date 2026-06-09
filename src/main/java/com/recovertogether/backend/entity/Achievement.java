package com.recovertogether.backend.entity;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@Entity
@Table(name = "achievements", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","title"}))
public class Achievement
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private LocalDateTime earnedAt;

    @ManyToOne
    @JoinColumn(name="user_id")
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

