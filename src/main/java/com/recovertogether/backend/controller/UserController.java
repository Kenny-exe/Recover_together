package com.recovertogether.backend.controller;
import java.util.List;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.dto.RegisterRequest;
import com.recovertogether.backend.dto.UserResponse;
import com.recovertogether.backend.dto.ChangePasswordRequest;
import com.recovertogether.backend.dto.UpdateProfileRequest;
import java.util.Map;
import com.recovertogether.backend.service.UserService;
import com.recovertogether.backend.service.UserTimeZone;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.service.AuditLogService;

@RestController
@RequestMapping("/users")
public class UserController
{
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public UserController(UserRepository userRepository, UserService userService, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    //CREATE
    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User saved = userService.register(request);
        return new UserResponse(saved);
    }

    //TARGETED SEARCH (exact email match for partner discovery; broad directory dump removed)
    @GetMapping("/search")
    public UserResponse searchByEmail(@RequestParam String email)
    {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email parameter is required");
        }

        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String normalizedEmail = email.trim().toLowerCase();

        if (currentUser.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot search for your own account");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return new UserResponse(user);
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id)
    {
        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserResponse(user);
    }

    //DELETE
    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!currentUser.getId().equals(id))
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot delete another user's account");
        }

        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        removeAccount(user);
        return "User deleted successfully";
    }

    //SELF-SERVICE DELETE (mobile account-deletion flow)
    @DeleteMapping("/me")
    public Map<String, String> deleteCurrentUser()
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        removeAccount(currentUser);
        return Map.of("message", "Account deleted successfully");
    }

    private void removeAccount(User target)
    {
        // DB-level ON DELETE CASCADE covers messages, notifications, check-ins,
        // refresh tokens, achievements, plans and support requests.
        userRepository.delete(target);
        auditLogService.log(AuditAction.ACCOUNT_DELETED, target.getId(), target.getEmail(), null);
    }

    //UPDATE
    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateProfileRequest request)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!currentUser.getId().equals(id))
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify another user's account");
        }

        User existingUser = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return applyProfileUpdate(existingUser, request);
    }

    //SELF-SERVICE UPDATE (mobile profile settings)
    @PutMapping("/me")
    public UserResponse updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return applyProfileUpdate(currentUser, request);
    }

    private UserResponse applyProfileUpdate(User target, UpdateProfileRequest request)
    {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedTimezone = UserTimeZone.normalize(request.getTimezone());

        if (!target.getEmail().equalsIgnoreCase(normalizedEmail)
                && userRepository.existsByEmail(normalizedEmail))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        target.setName(request.getName().trim());
        target.setEmail(normalizedEmail);

        if (normalizedTimezone != null) {
            target.setTimezone(normalizedTimezone);
        }

        User saved = userRepository.save(target);
        auditLogService.log(AuditAction.PROFILE_UPDATED, saved.getId(), saved.getEmail(), null);
        return new UserResponse(saved);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser()
    {
        User user=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new UserResponse(user);
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest request)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.changePassword(currentUser, request.getCurrentPassword(), request.getNewPassword());
        return Map.of("message", "Password changed successfully");
    }
}