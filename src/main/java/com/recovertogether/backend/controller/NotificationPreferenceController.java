package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.NotificationPreferencesRequest;
import com.recovertogether.backend.dto.NotificationPreferencesResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.NotificationPreferenceService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Push delivery preferences of the authenticated user. In-app notifications
 * are always delivered; these settings only gate mobile push.
 */
@RestController
@RequestMapping("/notifications/preferences")
public class NotificationPreferenceController
{
    private final NotificationPreferenceService notificationPreferenceService;

    public NotificationPreferenceController(NotificationPreferenceService notificationPreferenceService)
    {
        this.notificationPreferenceService = notificationPreferenceService;
    }

    @GetMapping
    public NotificationPreferencesResponse getPreferences()
    {
        return notificationPreferenceService.getPreferences(currentUser());
    }

    @PutMapping
    public NotificationPreferencesResponse updatePreferences(
            @RequestBody NotificationPreferencesRequest request)
    {
        return notificationPreferenceService.updatePreferences(currentUser(), request);
    }

    private User currentUser()
    {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
