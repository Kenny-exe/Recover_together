package com.recovertogether.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Re-authentication payload for self-service account deletion. Deleting an
 * account is destructive and irreversible, so the current password must be
 * presented again even though the caller already holds a valid access token.
 */
public class DeleteAccountRequest {

    @NotBlank(message = "Password is required")
    private String password;

    public DeleteAccountRequest() {
    }

    public DeleteAccountRequest(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
