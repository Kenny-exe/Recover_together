package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.ForgotPasswordRequest;
import com.recovertogether.backend.dto.ResetPasswordRequest;
import com.recovertogether.backend.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public account-recovery endpoints. Both return the same responses whether
 * or not the account exists, so they cannot be used to enumerate users.
 */
@RestController
@RequestMapping("/auth")
public class PasswordResetController {

    private static final String FORGOT_PASSWORD_MESSAGE =
            "If an account exists for that email, a reset code has been sent";
    private static final String RESET_PASSWORD_MESSAGE =
            "Password reset successfully";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return Map.of("message", FORGOT_PASSWORD_MESSAGE);
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return Map.of("message", RESET_PASSWORD_MESSAGE);
    }
}
