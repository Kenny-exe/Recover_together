package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registration of a push-device (FCM token) for the authenticated user.
 * Only these three fields are accepted: no other properties can be bound.
 */
public class DeviceRegistrationRequest {

    @NotNull(message = "Platform is required")
    private DevicePlatform platform;

    @NotBlank(message = "Push token is required")
    @Size(max = 4096, message = "Push token must be at most 4096 characters")
    private String token;

    @Size(max = 100, message = "Device name must be at most 100 characters")
    private String deviceName;

    public DeviceRegistrationRequest() {}

    public DeviceRegistrationRequest(DevicePlatform platform, String token, String deviceName) {
        this.platform = platform;
        this.token = token;
        this.deviceName = deviceName;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(DevicePlatform platform) {
        this.platform = platform;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
}
