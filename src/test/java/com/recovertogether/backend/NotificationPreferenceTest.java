package com.recovertogether.backend;

import com.recovertogether.backend.controller.NotificationPreferenceController;
import com.recovertogether.backend.dto.NotificationPreferencesRequest;
import com.recovertogether.backend.dto.NotificationPreferencesResponse;
import com.recovertogether.backend.entity.NotificationPreference;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.repository.NotificationPreferenceRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.NotificationPreferenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceTest {

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Mock
    private UserRepository userRepository;

    private NotificationPreferenceService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(
                notificationPreferenceRepository,
                userRepository);

        user = new User();
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setTimezone("UTC");
        setId(user, 1L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void quietHours(LocalTime start, LocalTime end) {
        user.setQuietHoursEnabled(true);
        user.setQuietHoursStart(start);
        user.setQuietHoursEnd(end);
    }

    // ---------------------------------------------------------------- defaults

    @Test
    @DisplayName("DEFAULTS: no stored rows -> push on, quiet hours off, every type enabled")
    void testDefaults() {
        when(notificationPreferenceRepository.findByUser(user)).thenReturn(List.of());

        NotificationPreferencesResponse response = service.getPreferences(user);

        assertTrue(response.isPushNotificationsEnabled());
        assertFalse(response.isQuietHoursEnabled());
        assertNull(response.getQuietHoursStart());
        assertNull(response.getQuietHoursEnd());

        Map<NotificationType, Boolean> typePreferences = response.getTypePreferences();
        assertEquals(NotificationType.values().length, typePreferences.size());
        for (NotificationType type : NotificationType.values()) {
            assertTrue(typePreferences.get(type), "Expected " + type + " to default to enabled");
        }
    }

    @Test
    @DisplayName("DEFAULTS: stored per-type rows override defaults; SOS stays enabled")
    void testStoredTypePreferencesOverrideDefaults() {
        when(notificationPreferenceRepository.findByUser(user)).thenReturn(List.of(
                new NotificationPreference(user, NotificationType.NEW_MESSAGE, false),
                new NotificationPreference(user, NotificationType.SUPPORT_REQUEST, false),
                new NotificationPreference(user, NotificationType.SOS_ALERT, false)
        ));

        Map<NotificationType, Boolean> typePreferences = service.getPreferences(user).getTypePreferences();

        assertFalse(typePreferences.get(NotificationType.NEW_MESSAGE));
        assertFalse(typePreferences.get(NotificationType.SUPPORT_REQUEST));
        assertTrue(typePreferences.get(NotificationType.PARTNER_RELAPSE));
        assertTrue(typePreferences.get(NotificationType.SOS_ALERT),
                "SOS must always report enabled, even if a stale row says otherwise");
    }

    // -------------------------------------------------------------- shouldPush

    @Test
    @DisplayName("PUSH: global switch disables every non-SOS push, SOS bypasses it")
    void testGlobalSwitchSuppressesNonSosPush() {
        user.setPushNotificationsEnabled(false);

        assertFalse(service.shouldPush(user, NotificationType.NEW_MESSAGE));
        assertFalse(service.shouldPush(user, NotificationType.DAILY_CHECKIN_REMINDER));
        assertTrue(service.shouldPush(user, NotificationType.SOS_ALERT));
    }

    @Test
    @DisplayName("PUSH: per-type row disables only that type")
    void testPerTypePreference() {
        when(notificationPreferenceRepository.findByUserAndType(user, NotificationType.NEW_MESSAGE))
                .thenReturn(Optional.of(new NotificationPreference(user, NotificationType.NEW_MESSAGE, false)));

        assertFalse(service.shouldPush(user, NotificationType.NEW_MESSAGE));
        assertTrue(service.shouldPush(user, NotificationType.PARTNER_RELAPSE));
    }

    @Test
    @DisplayName("PUSH: quiet hours suppress non-SOS push but SOS always goes through")
    void testQuietHoursSuppressPushNow() {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        quietHours(now.minusMinutes(30), now.plusMinutes(30));

        assertFalse(service.shouldPush(user, NotificationType.DAILY_CHECKIN_REMINDER));
        assertFalse(service.shouldPush(user, NotificationType.NEW_MESSAGE));
        assertTrue(service.shouldPush(user, NotificationType.SOS_ALERT));
    }

    // ------------------------------------------------------------- quiet hours

    @Test
    @DisplayName("QUIET HOURS: same-day window is [start, end)")
    void testSameDayQuietHoursBoundaries() {
        quietHours(LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T08:59:59Z")));
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T09:00:00Z")));
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T12:00:00Z")));
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T16:59:59Z")));
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T17:00:00Z")));
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T23:00:00Z")));
    }

    @Test
    @DisplayName("QUIET HOURS: overnight window (22:00-07:00) wraps midnight")
    void testOvernightQuietHoursBoundaries() {
        quietHours(LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T21:59:59Z")));
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T22:00:00Z")));
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T23:30:00Z")));
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T00:00:00Z")));
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T06:59:59Z")));
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T07:00:00Z")));
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T12:00:00Z")));
    }

    @Test
    @DisplayName("QUIET HOURS: evaluated on the user's local clock, not the server's")
    void testQuietHoursUseUserTimezone() {
        user.setTimezone("Asia/Kolkata");
        quietHours(LocalTime.of(22, 0), LocalTime.of(7, 0));

        // 18:00 UTC == 23:30 IST -> quiet.
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T18:00:00Z")));
        // 12:00 UTC == 17:30 IST -> not quiet.
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T12:00:00Z")));
        // 01:00 UTC == 06:30 IST -> quiet.
        assertTrue(service.isInQuietHours(user, Instant.parse("2026-10-28T01:00:00Z")));
        // 01:30 UTC == 07:00 IST -> window ended.
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T01:30:00Z")));
    }

    @Test
    @DisplayName("QUIET HOURS: disabled, incomplete or empty windows never suppress push")
    void testDegenerateQuietHoursNeverSuppress() {
        user.setQuietHoursStart(LocalTime.of(22, 0));
        user.setQuietHoursEnd(LocalTime.of(7, 0));
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T23:00:00Z")),
                "quiet hours are disabled");

        user.setQuietHoursEnabled(true);
        user.setQuietHoursStart(null);
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T23:00:00Z")));

        user.setQuietHoursStart(LocalTime.of(7, 0));
        assertFalse(service.isInQuietHours(user, Instant.parse("2026-10-28T07:00:00Z")),
                "start == end is treated as an empty window");
    }

    // ---------------------------------------------------------------- updates

    @Test
    @DisplayName("UPDATE: global switch and quiet hours are persisted and returned")
    void testUpdateGlobalAndQuietHours() {
        when(userRepository.save(user)).thenReturn(user);
        when(notificationPreferenceRepository.findByUser(user)).thenReturn(List.of());

        NotificationPreferencesResponse response = service.updatePreferences(user,
                new NotificationPreferencesRequest(false, true, "22:00", "07:00", null));

        assertFalse(response.isPushNotificationsEnabled());
        assertTrue(response.isQuietHoursEnabled());
        assertEquals("22:00", response.getQuietHoursStart());
        assertEquals("07:00", response.getQuietHoursEnd());
        assertFalse(user.isPushNotificationsEnabled());
        verify(userRepository).save(user);
        verify(notificationPreferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPDATE: enabling quiet hours without both times is rejected and not applied")
    void testUpdateQuietHoursRequiresTimes() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updatePreferences(user,
                        new NotificationPreferencesRequest(null, true, null, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("required"));
        assertFalse(user.isQuietHoursEnabled());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPDATE: equal start and end times are rejected")
    void testUpdateRejectsEmptyQuietWindow() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updatePreferences(user,
                        new NotificationPreferencesRequest(null, true, "22:00", "22:00", null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("different"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPDATE: malformed quiet-hours time is rejected with a helpful message")
    void testUpdateRejectsInvalidTime() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updatePreferences(user,
                        new NotificationPreferencesRequest(null, true, "25:00", "07:00", null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid quiet hours start time"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPDATE: supplied type preferences are upserted, case-insensitively")
    void testUpdateTypePreferences() {
        List<NotificationPreference> stored = new ArrayList<>();

        when(notificationPreferenceRepository.findByUserAndType(eq(user), any(NotificationType.class)))
                .thenAnswer(invocation -> stored.stream()
                        .filter(p -> p.getType() == invocation.getArgument(1))
                        .findFirst());
        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> {
                    NotificationPreference preference = invocation.getArgument(0);
                    stored.add(preference);
                    return preference;
                });
        when(notificationPreferenceRepository.findByUser(user)).thenAnswer(invocation -> stored);

        Map<String, Boolean> typePreferences = new LinkedHashMap<>();
        typePreferences.put("NEW_MESSAGE", false);
        typePreferences.put("missed_checkin", true);

        NotificationPreferencesResponse response = service.updatePreferences(user,
                new NotificationPreferencesRequest(null, null, null, null, typePreferences));

        assertEquals(2, stored.size());
        assertFalse(response.getTypePreferences().get(NotificationType.NEW_MESSAGE));
        assertTrue(response.getTypePreferences().get(NotificationType.MISSED_CHECKIN));
        assertTrue(response.getTypePreferences().get(NotificationType.SOS_ALERT));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPDATE: an existing type row is updated in place rather than duplicated")
    void testUpdateTypePreferenceUpdatesExistingRow() {
        NotificationPreference existing =
                new NotificationPreference(user, NotificationType.NEW_MESSAGE, true);

        when(notificationPreferenceRepository.findByUserAndType(user, NotificationType.NEW_MESSAGE))
                .thenReturn(Optional.of(existing));
        when(notificationPreferenceRepository.save(existing)).thenReturn(existing);
        when(notificationPreferenceRepository.findByUser(user)).thenReturn(List.of(existing));

        service.updatePreferences(user, new NotificationPreferencesRequest(
                null, null, null, null, Map.of("NEW_MESSAGE", false)));

        assertFalse(existing.isPushEnabled());
        verify(notificationPreferenceRepository).save(existing);
    }

    @Test
    @DisplayName("UPDATE: SOS alerts cannot be disabled")
    void testUpdateRejectsDisablingSos() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updatePreferences(user, new NotificationPreferencesRequest(
                        null, null, null, null, Map.of("SOS_ALERT", false))));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("always delivered"));
        verify(notificationPreferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPDATE: unknown notification types are rejected")
    void testUpdateRejectsUnknownType() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updatePreferences(user, new NotificationPreferencesRequest(
                        null, null, null, null, Map.of("NOT_A_TYPE", true))));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Unknown notification type"));
        verify(notificationPreferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("UPDATE: blank times clear stored quiet hours when disabling them")
    void testUpdateClearsQuietHours() {
        user.setQuietHoursEnabled(true);
        user.setQuietHoursStart(LocalTime.of(22, 0));
        user.setQuietHoursEnd(LocalTime.of(7, 0));

        when(userRepository.save(user)).thenReturn(user);
        when(notificationPreferenceRepository.findByUser(user)).thenReturn(List.of());

        NotificationPreferencesResponse response = service.updatePreferences(user,
                new NotificationPreferencesRequest(false, false, "", "", null));

        assertFalse(response.isQuietHoursEnabled());
        assertNull(response.getQuietHoursStart());
        assertNull(response.getQuietHoursEnd());
        assertNull(user.getQuietHoursStart());
        assertNull(user.getQuietHoursEnd());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("UPDATE: null request body is rejected")
    void testUpdateNullRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updatePreferences(user, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // --------------------------------------------------------------- controller

    @Test
    @DisplayName("CONTROLLER: GET /notifications/preferences returns the authenticated user's settings")
    void testControllerGetPreferences() {
        when(notificationPreferenceRepository.findByUser(user)).thenReturn(List.of());
        NotificationPreferenceController controller =
                new NotificationPreferenceController(service);

        NotificationPreferencesResponse response = controller.getPreferences();

        assertTrue(response.isPushNotificationsEnabled());
        assertEquals(NotificationType.values().length, response.getTypePreferences().size());
    }

    @Test
    @DisplayName("CONTROLLER: PUT /notifications/preferences applies the authenticated user's update")
    void testControllerUpdatePreferences() {
        when(userRepository.save(user)).thenReturn(user);
        when(notificationPreferenceRepository.findByUser(user)).thenReturn(List.of());
        NotificationPreferenceController controller =
                new NotificationPreferenceController(service);

        NotificationPreferencesResponse response = controller.updatePreferences(
                new NotificationPreferencesRequest(false, false, null, null, null));

        assertFalse(response.isPushNotificationsEnabled());
        verify(userRepository).save(user);
    }
}
