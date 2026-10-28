package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.DeviceRegistrationRequest;
import com.recovertogether.backend.dto.DeviceResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.entity.UserDevice;
import com.recovertogether.backend.repository.UserDeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Manages registered push devices of the authenticated user.
 *
 * Tokens are unique per application-installation. When a token shows up
 * under a different owner (app reinstall / device handoff), the previous
 * binding is replaced — the old owner simply stops receiving pushes there.
 */
@Service
public class DeviceService
{
    private final UserDeviceRepository userDeviceRepository;

    public DeviceService(UserDeviceRepository userDeviceRepository)
    {
        this.userDeviceRepository = userDeviceRepository;
    }

    @Transactional
    public DeviceResponse registerDevice(User currentUser, DeviceRegistrationRequest request)
    {
        String token = request.getToken().trim();

        UserDevice device = userDeviceRepository.findByToken(token).orElseGet(UserDevice::new);

        device.setUser(currentUser);
        device.setPlatform(request.getPlatform());
        device.setToken(token);
        device.setDeviceName(request.getDeviceName() == null ? null : request.getDeviceName().trim());
        device.setActive(true);
        device.setUpdatedAt(java.time.LocalDateTime.now());

        UserDevice saved = userDeviceRepository.save(device);
        return DeviceResponse.from(saved);
    }

    public List<DeviceResponse> getDevices(User currentUser)
    {
        return userDeviceRepository.findByUser(currentUser).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional
    public void removeDevice(User currentUser, Long deviceId)
    {
        UserDevice device = userDeviceRepository.findByIdAndUser(deviceId, currentUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        userDeviceRepository.delete(device);
    }

    /**
     * Deactivates every device of the user (used by logout-everywhere style
     * flows). Devices remain listed but inactive until re-registered.
     */
    @Transactional
    public void deactivateAll(User currentUser)
    {
        List<UserDevice> devices = userDeviceRepository.findByUserAndActiveTrue(currentUser);
        for (UserDevice device : devices)
        {
            device.setActive(false);
        }
        userDeviceRepository.saveAll(devices);
    }

    public Optional<UserDevice> findByToken(String token)
    {
        return userDeviceRepository.findByToken(token);
    }

    public long countActive(User currentUser)
    {
        return userDeviceRepository.findByUserAndActiveTrue(currentUser).size();
    }
}
