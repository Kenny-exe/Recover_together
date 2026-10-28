package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;

/**
 * Fan-out abstraction for mobile push notifications. Implementations must
 * never throw: push failures are logged and must not break the primary
 * domain workflow (e.g. saving a notification or message).
 */
public interface PushNotificationService
{
    void push(User receiver, String title, String body);
}
