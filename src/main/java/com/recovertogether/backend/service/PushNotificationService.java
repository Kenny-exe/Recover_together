package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;

import java.util.Map;

/**
 * Fan-out abstraction for mobile push notifications. Implementations must
 * never throw: push failures are logged and must not break the primary
 * domain workflow (e.g. saving a notification or message).
 *
 * Data payloads are always string-to-string (FCM requirement); they carry
 * the notification type and a client-side screen route for deep linking.
 */
public interface PushNotificationService
{
    void push(User receiver, String title, String body);

    default void push(User receiver, String title, String body, Map<String, String> data)
    {
        push(receiver, title, body);
    }
}
