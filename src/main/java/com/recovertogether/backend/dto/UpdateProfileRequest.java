package com.recovertogether.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile update. Sealed to name + email only so sensitive
 * fields (password, lockout, token-invalidation config) can never be bound.
 */
public class UpdateProfileRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 64, message = "Timezone must be at most 64 characters")
    private String timezone;

    public UpdateProfileRequest() {}

    public UpdateProfileRequest(String name, String email) {
        this(name, email, null);
    }

    public UpdateProfileRequest(String name, String email, String timezone) {
        this.name = name;
        this.email = email;
        this.timezone = timezone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
