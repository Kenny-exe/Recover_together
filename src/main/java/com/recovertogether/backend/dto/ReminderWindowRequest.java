package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.ReminderWindow;
import jakarta.validation.constraints.NotNull;

public class ReminderWindowRequest {

    @NotNull(message = "Reminder window is required")
    private ReminderWindow window;

    public ReminderWindowRequest() {
    }

    public ReminderWindowRequest(ReminderWindow window) {
        this.window = window;
    }

    public ReminderWindow getWindow() {
        return window;
    }

    public void setWindow(ReminderWindow window) {
        this.window = window;
    }
}
