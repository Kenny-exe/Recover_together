package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    @org.springframework.beans.factory.annotation.Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
    }

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService) {
        this(userRepository, passwordEncoder, auditLogService, null);
    }

    public User register(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepository.save(user);
        auditLogService.log(AuditAction.USER_REGISTERED, saved.getId(), saved.getEmail(), null);
        return saved;
    }

    public String login(String email, String password) {
        authenticate(email, password);
        return "Login Successful";
    }

    public User authenticate(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            auditLogService.log(AuditAction.LOGIN_FAILURE, null, email, "User not found");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userOpt.get();

        // Check brute-force account lockout
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(now)) {
            // Locked accounts cannot perform BCrypt password matching while locked
            auditLogService.log(AuditAction.LOGIN_FAILURE, user.getId(), user.getEmail(), "Account locked");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Lockout expired: reset lockout timestamp
        if (user.getLockoutUntil() != null && !user.getLockoutUntil().isAfter(now)) {
            user.setLockoutUntil(null);
            user.setFailedLoginAttempts(0);
        }

        boolean matches = passwordEncoder.matches(password, user.getPassword());
        if (!matches) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockoutUntil(now.plusMinutes(LOCKOUT_MINUTES));
                userRepository.save(user);
                auditLogService.log(AuditAction.ACCOUNT_LOCKED, user.getId(), user.getEmail(),
                        "Account locked after " + attempts + " failed attempts");
                auditLogService.log(AuditAction.LOGIN_FAILURE, user.getId(), user.getEmail(), "Invalid password");
            } else {
                userRepository.save(user);
                auditLogService.log(AuditAction.LOGIN_FAILURE, user.getId(), user.getEmail(), "Invalid password");
            }

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Successful login
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);

        auditLogService.log(AuditAction.LOGIN_SUCCESS, user.getId(), user.getEmail(), null);
        return user;
    }

    public void changePassword(User currentUser, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password does not match");
        }

        if (passwordEncoder.matches(newPassword, currentUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password cannot be the same as current password");
        }

        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be between 8 and 128 characters");
        }

        currentUser.setPassword(passwordEncoder.encode(newPassword));
        currentUser.setTokensInvalidatedBefore(LocalDateTime.now());
        userRepository.save(currentUser);

        if (refreshTokenService != null) {
            refreshTokenService.revokeAllForUser(currentUser);
        }

        auditLogService.log(AuditAction.PASSWORD_CHANGED, currentUser.getId(), currentUser.getEmail(), null);
    }
}