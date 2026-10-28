package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.DevicePlatform;
import java.time.LocalDateTime;

/**
 * A registered push device as visible to its owner. The raw push token is
 * never returned: only a short masked preview so the user can recognize it.
 */
public class DeviceResponse {

    private Long id;
    private DevicePlatform platform;
    private String deviceName;
    private String tokenPreview;
    private boolean active;
    private LocalDateTime createdAt;

    public DeviceResponse() {}

    public DeviceResponse(Long id, DevicePlatform platform, String deviceName,
                          String tokenPreview, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.platform = platform;
        this.deviceName = deviceName;
        this.tokenPreview = tokenPreview;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static DeviceResponse from(com.recovertogether.backend.entity.UserDevice device) {
        String token = device.getToken() == null ? "" : device.getToken();
        String preview = token.length() <= 6 ? token : token.substring(0, 6) + "...";
        return new DeviceResponse(
                device.getId(),
                device.getPlatform(),
                device.getDeviceName(),
                preview,
                device.isActive(),
                device.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getTokenPreview() {
        return tokenPreview;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
