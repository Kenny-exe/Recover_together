package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.NotificationPreferencesRequest;
import com.recovertogether.backend.dto.NotificationPreferencesResponse;
import com.recovertogether.backend.entity.NotificationPreference;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.repository.NotificationPreferenceRepository;
import com.recovertogether.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves whether a mobile push should be delivered for a notification.
 *
 * <p>In-app notifications and the SSE stream are always delivered; this
 * service only gates FCM push. Preferences are evaluated as:</p>
 * <ol>
 *   <li>{@code SOS_ALERT} always pushes, bypassing every preference and
 *       quiet hours (safety-critical).</li>
 *   <li>The global push switch disables all non-SOS push.</li>
 *   <li>A per-type row may disable that type (missing row means enabled).</li>
 *   <li>Quiet hours suppress non-SOS push inside the window, measured on the
 *       user's local clock. Start is inclusive, end exclusive; overnight
 *       windows such as 22:00-07:00 are supported.</li>
 * </ol>
 *
 * <p>Missing or invalid stored quiet-hours values never suppress push; only a
 * fully specified, non-empty window does. Writes reject invalid values.</p>
 */
@Service
public class NotificationPreferenceService
{
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserRepository userRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository notificationPreferenceRepository,
                                         UserRepository userRepository)
    {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(User user)
    {
        return new NotificationPreferencesResponse(
                user.isPushNotificationsEnabled(),
                user.isQuietHoursEnabled(),
                user.getQuietHoursStart(),
                user.getQuietHoursEnd(),
                typePreferences(user));
    }

    /**
     * Patch-style update: null fields keep their current value, blank
     * quiet-hours times clear the stored value, and only supplied type
     * preferences are written.
     */
    @Transactional
    public NotificationPreferencesResponse updatePreferences(User user,
                                                             NotificationPreferencesRequest request)
    {
        if (request == null)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Notification preferences are required");
        }

        boolean pushEnabled = request.getPushNotificationsEnabled() != null
                ? request.getPushNotificationsEnabled()
                : user.isPushNotificationsEnabled();

        boolean quietEnabled = request.getQuietHoursEnabled() != null
                ? request.getQuietHoursEnabled()
                : user.isQuietHoursEnabled();

        LocalTime quietStart = request.getQuietHoursStart() != null
                ? parseQuietHoursTime("start", request.getQuietHoursStart())
                : user.getQuietHoursStart();

        LocalTime quietEnd = request.getQuietHoursEnd() != null
                ? parseQuietHoursTime("end", request.getQuietHoursEnd())
                : user.getQuietHoursEnd();

        if (quietEnabled)
        {
            if (quietStart == null || quietEnd == null)
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Quiet hours start and end times are required when quiet hours are enabled");
            }
            if (quietStart.equals(quietEnd))
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Quiet hours start and end times must be different");
            }
        }

        boolean userChanged = request.getPushNotificationsEnabled() != null
                || request.getQuietHoursEnabled() != null
                || request.getQuietHoursStart() != null
                || request.getQuietHoursEnd() != null;

        user.setPushNotificationsEnabled(pushEnabled);
        user.setQuietHoursEnabled(quietEnabled);
        user.setQuietHoursStart(quietStart);
        user.setQuietHoursEnd(quietEnd);

        if (request.getTypePreferences() != null)
        {
            for (Map.Entry<String, Boolean> entry : request.getTypePreferences().entrySet())
            {
                applyTypePreference(user, entry.getKey(), entry.getValue());
            }
        }

        if (userChanged)
        {
            userRepository.save(user);
        }

        return getPreferences(user);
    }

    /**
     * @return true when a mobile push should be attempted for this user and
     * notification type. Callers must never treat this as a delivery
     * guarantee: FCM delivery itself remains best-effort.
     */
    public boolean shouldPush(User user, NotificationType type)
    {
        if (type == NotificationType.SOS_ALERT)
        {
            return true;
        }
        if (user == null || !user.isPushNotificationsEnabled())
        {
            return false;
        }
        if (type != null && !isTypePushEnabled(user, type))
        {
            return false;
        }
        return !isInQuietHours(user, Instant.now());
    }

    /**
     * Quiet-hours check for an explicit instant, exposed for deterministic
     * tests. A window is [start, end): start inclusive, end exclusive.
     */
    public boolean isInQuietHours(User user, Instant instant)
    {
        if (user == null || !user.isQuietHoursEnabled())
        {
            return false;
        }

        LocalTime start = user.getQuietHoursStart();
        LocalTime end = user.getQuietHoursEnd();

        // Defensive: incomplete or empty windows never suppress push.
        if (start == null || end == null || start.equals(end))
        {
            return false;
        }

        LocalTime localTime = UserTimeZone.timeIn(user, instant);

        if (start.isBefore(end))
        {
            return !localTime.isBefore(start) && localTime.isBefore(end);
        }

        // Overnight window, e.g. 22:00 -> 07:00.
        return !localTime.isBefore(start) || localTime.isBefore(end);
    }

    private boolean isTypePushEnabled(User user, NotificationType type)
    {
        return notificationPreferenceRepository.findByUserAndType(user, type)
                .map(NotificationPreference::isPushEnabled)
                .orElse(true);
    }

    private Map<NotificationType, Boolean> typePreferences(User user)
    {
        Map<NotificationType, Boolean> preferences = new LinkedHashMap<>();
        for (NotificationType type : NotificationType.values())
        {
            preferences.put(type, true);
        }

        for (NotificationPreference preference : notificationPreferenceRepository.findByUser(user))
        {
            if (preference.getType() != null)
            {
                preferences.put(preference.getType(), preference.isPushEnabled());
            }
        }

        // Safety-critical alerts are never suppressible.
        preferences.put(NotificationType.SOS_ALERT, true);
        return preferences;
    }

    private void applyTypePreference(User user, String rawType, Boolean enabled)
    {
        NotificationType type = parseType(rawType);

        if (enabled == null)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Push preference for " + type + " must be true or false");
        }
        if (type == NotificationType.SOS_ALERT && !enabled)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SOS alerts are always delivered and cannot be disabled");
        }

        NotificationPreference preference = notificationPreferenceRepository
                .findByUserAndType(user, type)
                .orElseGet(() -> new NotificationPreference(user, type, true));

        preference.setPushEnabled(enabled);
        notificationPreferenceRepository.save(preference);
    }

    private NotificationType parseType(String rawType)
    {
        if (rawType == null || rawType.isBlank())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Notification type is required");
        }

        try
        {
            return NotificationType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown notification type: " + rawType.trim());
        }
    }

    private LocalTime parseQuietHoursTime(String boundary, String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        try
        {
            return LocalTime.parse(value.trim());
        }
        catch (DateTimeParseException e)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid quiet hours " + boundary + " time. Use HH:mm");
        }
    }
}
