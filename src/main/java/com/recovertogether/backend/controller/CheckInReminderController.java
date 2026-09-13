package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.ReminderWindowRequest;
import com.recovertogether.backend.dto.ReminderWindowResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.CheckInReminderService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkin/reminder-window")
public class CheckInReminderController {

    private final CheckInReminderService checkInReminderService;

    public CheckInReminderController(CheckInReminderService checkInReminderService) {
        this.checkInReminderService = checkInReminderService;
    }

    @GetMapping
    public ReminderWindowResponse getReminderWindow() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return checkInReminderService.getReminderWindow(currentUser);
    }

    @PutMapping
    public ReminderWindowResponse updateReminderWindow(@Valid @RequestBody ReminderWindowRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return checkInReminderService.updateReminderWindow(currentUser, request.getWindow());
    }
}
