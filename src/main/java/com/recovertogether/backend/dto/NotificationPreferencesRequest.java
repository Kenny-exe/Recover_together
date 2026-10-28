package com.recovertogether.backend.dto;

import java.util.Map;

/**
 * Patch-style notification preference update. Omitted (null) fields keep their
 * current value; a blank quiet-hours time clears the stored time. Times use
 * 24-hour {@code HH:mm} format and are interpreted in the user's timezone.
 *
 * <p>{@code typePreferences} maps {@link com.recovertogether.backend.enums.NotificationType}
 * names to their push toggle, e.g. {@code {"NEW_MESSAGE": false}}.</p>
 */
public class NotificationPreferencesRequest
{
    private Boolean pushNotificationsEnabled;
    private Boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;
    private Map<String, Boolean> typePreferences;

    public NotificationPreferencesRequest()
    {
    }

    public NotificationPreferencesRequest(Boolean pushNotificationsEnabled,
                                          Boolean quietHoursEnabled,
                                          String quietHoursStart,
                                          String quietHoursEnd,
                                          Map<String, Boolean> typePreferences)
    {
        this.pushNotificationsEnabled = pushNotificationsEnabled;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.typePreferences = typePreferences;
    }

    public Boolean getPushNotificationsEnabled() {return pushNotificationsEnabled;}

    public void setPushNotificationsEnabled(Boolean pushNotificationsEnabled) {this.pushNotificationsEnabled = pushNotificationsEnabled;}

    public Boolean getQuietHoursEnabled() {return quietHoursEnabled;}

    public void setQuietHoursEnabled(Boolean quietHoursEnabled) {this.quietHoursEnabled = quietHoursEnabled;}

    public String getQuietHoursStart() {return quietHoursStart;}

    public void setQuietHoursStart(String quietHoursStart) {this.quietHoursStart = quietHoursStart;}

    public String getQuietHoursEnd() {return quietHoursEnd;}

    public void setQuietHoursEnd(String quietHoursEnd) {this.quietHoursEnd = quietHoursEnd;}

    public Map<String, Boolean> getTypePreferences() {return typePreferences;}

    public void setTypePreferences(Map<String, Boolean> typePreferences) {this.typePreferences = typePreferences;}
}
