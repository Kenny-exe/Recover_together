package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.enums.CheckInStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PartnerCheckInResponse {

    private Long id;
    private LocalDate date;
    private CheckInStatus status;
    private LocalDateTime createdAt;

    public PartnerCheckInResponse() {}

    public PartnerCheckInResponse(DailyCheckIn checkIn) {
        this.id = checkIn.getId();
        this.date = checkIn.getDate();
        this.status = checkIn.getStatus();
        this.createdAt = checkIn.getCreatedAt();
    }

    public PartnerCheckInResponse(Long id, LocalDate date, CheckInStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.date = date;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public CheckInStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
