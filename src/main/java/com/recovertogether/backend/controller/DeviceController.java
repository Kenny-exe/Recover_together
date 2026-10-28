package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.DeviceRegistrationRequest;
import com.recovertogether.backend.dto.DeviceResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Push-device registry of the authenticated user (mobile clients call this
 * on app start / login with their FCM token).
 */
@RestController
@RequestMapping("/users/me/devices")
public class DeviceController
{
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService)
    {
        this.deviceService = deviceService;
    }

    @PostMapping
    public DeviceResponse registerDevice(@Valid @RequestBody DeviceRegistrationRequest request)
    {
        return deviceService.registerDevice(currentUser(), request);
    }

    @GetMapping
    public List<DeviceResponse> getDevices()
    {
        return deviceService.getDevices(currentUser());
    }

    @DeleteMapping("/{deviceId}")
    public Map<String, String> removeDevice(@PathVariable Long deviceId)
    {
        deviceService.removeDevice(currentUser(), deviceId);
        return Map.of("message", "Device removed");
    }

    private User currentUser()
    {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
