package com.recovertogether.backend.entity;

import com.recovertogether.backend.enums.CheckInStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_checkins")
public class DailyCheckIn
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    private LocalDate date;
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private CheckInStatus status;
    private String note;

    @PrePersist
    public void prePersist()
    {
        createdAt=LocalDateTime.now();
    }

    public Long getId() {return id;}

    public void setId(Long id) {this.id = id;}

    public User getUser() {return user;}

    public void setUser(User user) {this.user = user;}
    public LocalDate getDate(){return date;};
    public void setDate(LocalDate date){this.date=date;}

    public CheckInStatus getStatus() {return status;}

    public void setStatus(CheckInStatus status) {this.status = status;}

    public String getNote() {return note;}

    public void setNote(String note) {this.note = note;}

    public LocalDateTime getCreatedAt() {return createdAt;}

    public void setCreatedAt(LocalDateTime createdAt) {this.createdAt = createdAt;}
}
