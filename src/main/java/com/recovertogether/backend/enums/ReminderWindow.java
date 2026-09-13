package com.recovertogether.backend.enums;

import java.time.LocalTime;

public enum ReminderWindow {
    MORNING(LocalTime.of(8, 0), LocalTime.of(12, 0)),
    AFTERNOON(LocalTime.of(12, 0), LocalTime.of(17, 0)),
    EVENING(LocalTime.of(17, 0), LocalTime.of(22, 0)),
    ANYTIME(LocalTime.of(8, 0), LocalTime.of(22, 0));

    private final LocalTime startTime;
    private final LocalTime endTime;

    ReminderWindow(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
