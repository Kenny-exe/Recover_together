package com.recovertogether.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.recovertogether.backend.dto.DeviceRegistrationRequest;
import com.recovertogether.backend.dto.DeviceResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.entity.UserDevice;
import com.recovertogether.backend.enums.DevicePlatform;
import com.recovertogether.backend.repository.UserDeviceRepository;
import com.recovertogether.backend.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private com.recovertogether.backend.service.AuditLogService auditLogService;

    private DeviceService deviceService;

    private User currentUser;
    private User otherUser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(userDeviceRepository);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        currentUser = new User();
        currentUser.setName("Alice");
        currentUser.setEmail("alice@example.com");
        setId(currentUser, 1L);

        otherUser = new User();
        otherUser.setName("Bob");
        otherUser.setEmail("bob@example.com");
        setId(otherUser, 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, java.util.Collections.emptyList())
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

    private UserDevice ownedDevice(Long id, User owner, String token) {
        UserDevice device = new UserDevice();
        setId(device, id);
        device.setUser(owner);
        device.setPlatform(DevicePlatform.ANDROID);
        device.setToken(token);
        device.setDeviceName("Pixel 8");
        device.setActive(true);
        return device;
    }

    @Test
    @DisplayName("DEVICE REGISTER: new token creates an active device bound to the current user")
    void testRegisterNewToken() {
        DeviceRegistrationRequest request =
                new DeviceRegistrationRequest(DevicePlatform.ANDROID, "  fcm-token-abc  ", "Pixel 8");

        when(userDeviceRepository.findByToken("fcm-token-abc")).thenReturn(Optional.empty());
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceResponse response = deviceService.registerDevice(currentUser, request);

        assertNotNull(response);
        assertEquals(DevicePlatform.ANDROID, response.getPlatform());
        assertTrue(response.isActive());

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        UserDevice saved = captor.getValue();

        assertSame(currentUser, saved.getUser(), "Device must be bound to the current user");
        assertEquals("fcm-token-abc", saved.getToken(), "Token must be trimmed");
        assertEquals("Pixel 8", saved.getDeviceName());
        assertTrue(saved.isActive());
    }

    @Test
    @DisplayName("DEVICE REGISTER: token previously owned by another user is reassigned (app reinstall)")
    void testRegisterReassignsForeignToken() {
        DeviceRegistrationRequest request =
                new DeviceRegistrationRequest(DevicePlatform.IOS, "shared-fcm-token", "iPhone 15");

        UserDevice foreign = ownedDevice(10L, otherUser, "shared-fcm-token");
        when(userDeviceRepository.findByToken("shared-fcm-token")).thenReturn(Optional.of(foreign));
        when(userDeviceRepository.save(any(UserDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceResponse response = deviceService.registerDevice(currentUser, request);

        assertEquals(10L, response.getId(), "Existing row must be reused, not duplicated");

        ArgumentCaptor<UserDevice> captor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(captor.capture());
        assertSame(currentUser, captor.getValue().getUser(), "Stale owner binding must be replaced");
        assertTrue(captor.getValue().isActive(), "Re-registration must reactivate the device");
    }

    @Test
    @DisplayName("DEVICE LIST: tokens are never returned, only masked previews")
    void testListMasksTokens() throws Exception {
        String rawToken = "verylongfcmregistrationtoken0123456789abcdef";
        UserDevice device = ownedDevice(10L, currentUser, rawToken);

        when(userDeviceRepository.findByUser(currentUser)).thenReturn(List.of(device));

        List<DeviceResponse> devices = deviceService.getDevices(currentUser);

        assertEquals(1, devices.size());
        assertEquals("verylo...", devices.get(0).getTokenPreview());

        String json = objectMapper.writeValueAsString(devices);
        assertFalse(json.contains(rawToken), "JSON must never contain the raw push token");
        assertTrue(json.contains("tokenPreview"), "Response exposes only a masked preview");
    }

    @Test
    @DisplayName("DEVICE REMOVE: own device is deleted, unknown device is 404 and never deletes another user's device")
    void testRemoveDevice() {
        when(userDeviceRepository.findByIdAndUser(10L, currentUser))
                .thenReturn(Optional.of(ownedDevice(10L, currentUser, "token-a")));

        deviceService.removeDevice(currentUser, 10L);
        verify(userDeviceRepository).delete(any(UserDevice.class));

        when(userDeviceRepository.findByIdAndUser(11L, currentUser)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> deviceService.removeDevice(currentUser, 11L)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Device not found", ex.getReason());
    }

    @Test
    @DisplayName("DEVICE DEACTIVATE-ALL: all active devices are deactivated")
    void testDeactivateAll() {
        UserDevice active1 = ownedDevice(10L, currentUser, "token-a");
        UserDevice active2 = ownedDevice(11L, currentUser, "token-b");

        when(userDeviceRepository.findByUserAndActiveTrue(currentUser)).thenReturn(List.of(active1, active2));

        deviceService.deactivateAll(currentUser);

        ArgumentCaptor<List<UserDevice>> captor = ArgumentCaptor.forClass(List.class);
        verify(userDeviceRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().stream().noneMatch(UserDevice::isActive));
    }
}
