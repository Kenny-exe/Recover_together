package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.enums.CheckInStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CheckInResponse
{
    private Long id;
    private LocalDate date;
    private CheckInStatus status;
    private String note;
    private LocalDateTime createdAt;

    public CheckInResponse(DailyCheckIn checkIn)
    {
        this.id= checkIn.getId();
        this.date=checkIn.getDate();
        this.status=checkIn.getStatus();
        this.note= checkIn.getNote();
        this.createdAt=checkIn.getCreatedAt();
    }

    public Long getId() {return id;}

    public LocalDate getDate() {return date;}

    public CheckInStatus getStatus() {return status;}

    public String getNote() {return note;}

    public LocalDateTime getCreatedAt() {return createdAt;}
}