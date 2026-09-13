package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.LoginRequest;
import com.recovertogether.backend.dto.LoginResponse;
import com.recovertogether.backend.dto.LogoutRequest;
import com.recovertogether.backend.dto.RefreshTokenRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.service.AuditLogService;
import com.recovertogether.backend.service.JwtService;
import com.recovertogether.backend.service.RefreshTokenService;
import com.recovertogether.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    public AuthController(UserService userService,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          AuditLogService auditLogService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.getEmail(), request.getPassword());
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new LoginResponse(accessToken, refreshToken);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        String newAccessToken = jwtService.generateToken(result.user().getEmail());
        return new LoginResponse(newAccessToken, result.newRefreshToken());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestBody(required = false) LogoutRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (request != null && request.getRefreshToken() != null) {
            refreshTokenService.revokeRefreshToken(request.getRefreshToken(), currentUser);
        }

        SecurityContextHolder.clearContext();
        auditLogService.log(AuditAction.LOGOUT, currentUser.getId(), currentUser.getEmail(), null);

        return Map.of("message", "Logged out successfully");
    }
}