package com.recovertogether.backend;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.entity.UserDevice;
import com.recovertogether.backend.enums.DevicePlatform;
import com.recovertogether.backend.repository.UserDeviceRepository;
import com.recovertogether.backend.service.FcmPushService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private User receiver;

    @BeforeEach
    void setUp() {
        receiver = new User();
        receiver.setName("Alice");
        receiver.setEmail("alice@example.com");
        setId(receiver, 1L);
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

    private UserDevice device(Long id, String token, DevicePlatform platform) {
        UserDevice device = new UserDevice();
        setId(device, id);
        device.setUser(receiver);
        device.setPlatform(platform);
        device.setToken(token);
        device.setActive(true);
        return device;
    }

    private FcmPushService enabledService() {
        FcmPushService service = new FcmPushService(userDeviceRepository, "credentials.json");
        FcmPushService spy = spy(service);
        // lenient: some failure paths never reach obtainMessaging()
        lenient().doReturn(firebaseMessaging).when(spy).obtainMessaging();
        return spy;
    }

    @Test
    @DisplayName("PUSH DISABLED: without credentials nothing is sent and no error is raised")
    void testPushWithoutCredentialsIsSilentNoOp() {
        FcmPushService service = new FcmPushService(userDeviceRepository, "");
        when(userDeviceRepository.findByUserAndActiveTrue(receiver)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.push(receiver, "Title", "Body"));

        verifyNoInteractions(firebaseMessaging);
        verify(userDeviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUSH FAN-OUT: one FCM message per active device")
    void testPushFansOutToAllActiveDevices() throws Exception {
        when(userDeviceRepository.findByUserAndActiveTrue(receiver))
                .thenReturn(List.of(
                        device(10L, "token-a", DevicePlatform.ANDROID),
                        device(11L, "token-b", DevicePlatform.IOS)));

        FcmPushService service = enabledService();

        service.push(receiver, "SOS alert", "Alice triggered SOS");

        verify(firebaseMessaging, times(2)).send(any(Message.class));
        verifyNoMoreInteractions(firebaseMessaging);
        verify(userDeviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUSH FAN-OUT: unregistered token deactivates that device only")
    void testUnregisteredTokenDeactivatesDevice() throws Exception {
        UserDevice stale = device(10L, "stale-token", DevicePlatform.ANDROID);
        UserDevice healthy = device(11L, "healthy-token", DevicePlatform.IOS);
        when(userDeviceRepository.findByUserAndActiveTrue(receiver)).thenReturn(List.of(stale, healthy));

        FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
        when(unregistered.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        FcmPushService service = enabledService();
        when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(unregistered)   // first send: stale token
                .thenReturn("responseId"); // second send: healthy

        service.push(receiver, "Title", "Body");

        assertTrue(!stale.isActive(), "Stale device must be deactivated");
        assertTrue(healthy.isActive(), "Healthy device must stay active");
        verify(userDeviceRepository).save(stale);
    }

    @Test
    @DisplayName("PUSH FAILURE: transient errors neither deactivate devices nor propagate")
    void testTransientErrorDoesNotDeactivate() throws Exception {
        UserDevice device = device(10L, "token-a", DevicePlatform.ANDROID);
        when(userDeviceRepository.findByUserAndActiveTrue(receiver)).thenReturn(List.of(device));

        FirebaseMessagingException quota = mock(FirebaseMessagingException.class);
        when(quota.getMessagingErrorCode()).thenReturn(MessagingErrorCode.QUOTA_EXCEEDED);

        FcmPushService service = enabledService();
        when(firebaseMessaging.send(any(Message.class))).thenThrow(quota);

        assertDoesNotThrow(() -> service.push(receiver, "Title", "Body"));

        assertTrue(device.isActive(), "Transient failure must not deactivate the device");
        verify(userDeviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUSH FAILURE: repository errors are swallowed (never break callers)")
    void testRepositoryFailureSwallowed() {
        when(userDeviceRepository.findByUserAndActiveTrue(receiver))
                .thenThrow(new RuntimeException("db down"));

        FcmPushService service = enabledService();

        assertDoesNotThrow(() -> service.push(receiver, "Title", "Body"));
    }
}
