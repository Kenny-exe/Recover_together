package com.recovertogether.backend;

import com.recovertogether.backend.dto.NotificationResponse;
import com.recovertogether.backend.entity.Notification;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.repository.NotificationRepository;
import com.recovertogether.backend.service.NotificationService;
import com.recovertogether.backend.service.PushNotificationService;
import com.recovertogether.backend.service.RealtimeEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private RealtimeEventService realtimeEventService;

    private NotificationService notificationService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, pushNotificationService, realtimeEventService);

        currentUser = new User();
        currentUser.setName("Alice");
        currentUser.setEmail("alice@example.com");
        setId(currentUser, 1L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Notification notification(Long id, NotificationType type, String message) {
        Notification notification = new Notification();
        setId(notification, id);
        notification.setReceiver(currentUser);
        notification.setType(type);
        notification.setMessage(message);
        return notification;
    }

    @Test
    @DisplayName("LIST: without cursor uses recent-first query, honors limit bounds (1..100)")
    void testListWithoutCursorClampsLimit() {
        when(notificationRepository.findByReceiverOrderByCreatedAtDesc(currentUser)).thenReturn(List.of());

        List<NotificationResponse> zeroResult = notificationService.getNotifications(0, null);
        assertTrue(zeroResult.isEmpty());
        verify(notificationRepository).findByReceiverOrderByCreatedAtDesc(currentUser);

        // Re-stub is fine: same query executed again for the 500 case.
        when(notificationRepository.findByReceiverOrderByCreatedAtDesc(currentUser)).thenReturn(List.of());
        List<NotificationResponse> overflowResult = notificationService.getNotifications(500, null);
        assertTrue(overflowResult.isEmpty());
        verify(notificationRepository, times(2)).findByReceiverOrderByCreatedAtDesc(currentUser);

        verify(notificationRepository, never()).findByReceiverAndIdLessThanOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("LIST: with cursor delegates to the beforeId query")
    void testListWithCursor() {
        when(notificationRepository.findByReceiverAndIdLessThanOrderByCreatedAtDesc(currentUser, 50L))
                .thenReturn(List.of(notification(49L, NotificationType.NEW_MESSAGE, "older")));

        List<NotificationResponse> result = notificationService.getNotifications(20, 50L);

        assertEquals(1, result.size());
        verify(notificationRepository, never()).findByReceiverOrderByCreatedAtDesc(any());
        verify(notificationRepository).findByReceiverAndIdLessThanOrderByCreatedAtDesc(currentUser, 50L);
    }

    @Test
    @DisplayName("CREATE: saves notification, pushes mapped title + deep-link data, and emits SSE")
    void testCreateNotificationPushes() {
        notificationService.createNotification(currentUser, NotificationType.SOS_ALERT, "Alice triggered SOS");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(NotificationType.SOS_ALERT, captor.getValue().getType());
        assertSame(currentUser, captor.getValue().getReceiver());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> dataCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);

        verify(pushNotificationService).push(eq(currentUser), eq("SOS alert"), eq("Alice triggered SOS"),
                dataCaptor.capture());
        assertEquals("SOS_ALERT", dataCaptor.getValue().get("type"));
        assertEquals("sos", dataCaptor.getValue().get("screen"));

        verify(realtimeEventService).publish(eq(1L), eq("notification"), any());
    }

    @Test
    @DisplayName("CREATE: extra deep-link metadata (partnerId) is forwarded to the push payload")
    void testCreateNotificationForwardsExtraData() {
        notificationService.createNotification(currentUser, NotificationType.NEW_MESSAGE, "hi",
                java.util.Map.of("partnerId", "2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> dataCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);

        verify(pushNotificationService).push(eq(currentUser), eq("New message"), eq("hi"), dataCaptor.capture());
        assertEquals("chat", dataCaptor.getValue().get("screen"));
        assertEquals("2", dataCaptor.getValue().get("partnerId"));
    }

    @Test
    @DisplayName("CREATE: push and SSE failures never break notification creation")
    void testCreateNotificationSurvivesPushFailure() {
        doThrow(new RuntimeException("firebase down"))
                .when(pushNotificationService).push(any(), any(), any(), any());
        doThrow(new RuntimeException("sse down"))
                .when(realtimeEventService).publish(any(), any(), any());

        assertDoesNotThrow(() ->
                notificationService.createNotification(currentUser, NotificationType.NEW_MESSAGE, "hello"));

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("TITLES: every notification type maps to a human title, null maps to fallback")
    void testTitleMapping() {
        assertEquals("RecoverTogether", NotificationService.titleFor(null));
        assertEquals("New message", NotificationService.titleFor(NotificationType.NEW_MESSAGE));
        assertEquals("SOS alert", NotificationService.titleFor(NotificationType.SOS_ALERT));
        assertEquals("Partner update", NotificationService.titleFor(NotificationType.PARTNER_RELAPSE));
        assertEquals("Missed check-in", NotificationService.titleFor(NotificationType.MISSED_CHECKIN));
        assertEquals("Achievement unlocked", NotificationService.titleFor(NotificationType.ACHIEVEMENT_UNLOCKED));
        assertEquals("Partner achievement", NotificationService.titleFor(NotificationType.PARTNER_ACHIEVEMENT));
        assertEquals("Check-in reminder", NotificationService.titleFor(NotificationType.DAILY_CHECKIN_REMINDER));
        assertEquals("Support request", NotificationService.titleFor(NotificationType.SUPPORT_REQUEST));
        for (NotificationType type : NotificationType.values()) {
            assertNotNull(NotificationService.titleFor(type));
            assertFalse(NotificationService.titleFor(type).isBlank());
        }
    }
}
