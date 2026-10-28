package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.NotificationType;

import java.time.LocalTime;
import java.util.Map;

/**
 * Effective notification delivery settings. Every {@link NotificationType} is
 * present in {@code typePreferences}; types without a stored row default to
 * {@code true}. SOS alerts are always {@code true}.
 */
public class NotificationPreferencesResponse
{
    private boolean pushNotificationsEnabled;
    private boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;
    private Map<NotificationType, Boolean> typePreferences;

    public NotificationPreferencesResponse()
    {
    }

    public NotificationPreferencesResponse(boolean pushNotificationsEnabled,
                                           boolean quietHoursEnabled,
                                           LocalTime quietHoursStart,
                                           LocalTime quietHoursEnd,
                                           Map<NotificationType, Boolean> typePreferences)
    {
        this.pushNotificationsEnabled = pushNotificationsEnabled;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart == null ? null : quietHoursStart.toString();
        this.quietHoursEnd = quietHoursEnd == null ? null : quietHoursEnd.toString();
        this.typePreferences = typePreferences;
    }

    public boolean isPushNotificationsEnabled() {return pushNotificationsEnabled;}

    public void setPushNotificationsEnabled(boolean pushNotificationsEnabled) {this.pushNotificationsEnabled = pushNotificationsEnabled;}

    public boolean isQuietHoursEnabled() {return quietHoursEnabled;}

    public void setQuietHoursEnabled(boolean quietHoursEnabled) {this.quietHoursEnabled = quietHoursEnabled;}

    public String getQuietHoursStart() {return quietHoursStart;}

    public void setQuietHoursStart(String quietHoursStart) {this.quietHoursStart = quietHoursStart;}

    public String getQuietHoursEnd() {return quietHoursEnd;}

    public void setQuietHoursEnd(String quietHoursEnd) {this.quietHoursEnd = quietHoursEnd;}

    public Map<NotificationType, Boolean> getTypePreferences() {return typePreferences;}

    public void setTypePreferences(Map<NotificationType, Boolean> typePreferences) {this.typePreferences = typePreferences;}
}
