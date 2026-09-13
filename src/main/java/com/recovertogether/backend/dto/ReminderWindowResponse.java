package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.ReminderWindow;

public class ReminderWindowResponse {

    private ReminderWindow window;
    private String startTime;
    private String endTime;

    public ReminderWindowResponse() {
    }

    public ReminderWindowResponse(ReminderWindow window) {
        this.window = window;
        this.startTime = window.getStartTime().toString();
        this.endTime = window.getEndTime().toString();
    }

    public ReminderWindow getWindow() {
        return window;
    }

    public void setWindow(ReminderWindow window) {
        this.window = window;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
