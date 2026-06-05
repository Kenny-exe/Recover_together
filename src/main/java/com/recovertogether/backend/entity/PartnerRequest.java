package com.recovertogether.backend.entity;

import com.recovertogether.backend.enums.PartnerRequestStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "partner_requests", uniqueConstraints = {@UniqueConstraint(columnNames = {"sender_id","receiver_id"})})
public class PartnerRequest
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartnerRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist()
    {
        createdAt=LocalDateTime.now();
        if(status==null)
        {
            status=PartnerRequestStatus.PENDING;
        }
    }

    public Long getId() {
        return id;
    }
    public User getSender() {
        return sender;
    }
    public void setSender(User sender) {
        this.sender = sender;
    }
    public User getReceiver() {
        return receiver;
    }
    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }
    public PartnerRequestStatus getStatus()
    {
        return status;
    }
    public void setStatus(PartnerRequestStatus status) {
        this.status = status;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}


