package com.recovertogether.backend.controller;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.RealtimeEventService;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Foreground live-update stream for mobile clients.
 *
 * Events:
 *  - "connected"    {user}                      — stream established
 *  - "message"      ChatMessageResponse          — new chat message
 *  - "notification" NotificationResponse         — any notification created
 *
 * Kept open by server heartbeats; clients reconnect with exponential backoff.
 */
@RestController
public class EventController
{
    private final RealtimeEventService realtimeEventService;

    public EventController(RealtimeEventService realtimeEventService)
    {
        this.realtimeEventService = realtimeEventService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events()
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return realtimeEventService.subscribe(currentUser);
    }
}
