package com.recovertogether.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id",nullable = false)
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id",nullable = false)
    private User receiver;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist()
    {
        createdAt=LocalDateTime.now();
    }

    public Long getId() {return id;}

    public void setId(Long id) {this.id = id;}

    public User getSender() {return sender;}

    public void setSender(User sender) {this.sender = sender;}

    public User getReceiver() {return receiver;}

    public void setReceiver(User receiver) {this.receiver = receiver;}

    public String getContent() {return content;}

    public void setContent(String content) {this.content = content;}

    public LocalDateTime getCreatedAt() {return createdAt;}

    public void setCreatedAt(LocalDateTime createdAt) {this.createdAt = createdAt;}
}
