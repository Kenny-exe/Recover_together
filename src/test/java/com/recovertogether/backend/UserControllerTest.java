package com.recovertogether.backend;

import com.recovertogether.backend.controller.UserController;
import com.recovertogether.backend.dto.UserResponse;
import com.recovertogether.backend.dto.UpdateProfileRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private com.recovertogether.backend.service.AuditLogService auditLogService;

    @InjectMocks
    private UserController userController;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setName("Alice");
        currentUser.setEmail("alice@example.com");
        setId(currentUser, 1L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("PUT /users/{id} with another user's id throws 403 Forbidden (IDOR prevention)")
    void testUpdateOtherUserForbidden() {
        UpdateProfileRequest updatedInput = new UpdateProfileRequest("Attacker", "attacker@example.com");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.updateUser(2L, updatedInput)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("You cannot modify another user's account", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("DELETE /users/{id} with another user's id throws 403 Forbidden (IDOR prevention)")
    void testDeleteOtherUserForbidden() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.deleteUser(2L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("You cannot delete another user's account", ex.getReason());
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("PUT /users/{id} on own account successfully updates and returns UserResponse")
    void testUpdateOwnAccountSuccess() {
        UpdateProfileRequest updatedInput = new UpdateProfileRequest("Alice Updated", "alice_new@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.existsByEmail("alice_new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userController.updateUser(1L, updatedInput);

        assertNotNull(response);
        assertEquals("Alice Updated", response.getName());
        assertEquals("alice_new@example.com", response.getEmail());
        verify(userRepository).save(currentUser);
    }

    @Test
    @DisplayName("PUT /users/{id} on own account with duplicate email throws 400 Bad Request")
    void testUpdateOwnAccountDuplicateEmail() {
        UpdateProfileRequest updatedInput = new UpdateProfileRequest("Alice", "existing@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.updateUser(1L, updatedInput)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Email is already registered", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("DELETE /users/{id} on own account successfully deletes user")
    void testDeleteOwnAccountSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        String result = userController.deleteUser(1L);

        assertEquals("User deleted successfully", result);
        verify(userRepository).delete(currentUser);
    }

    @Test
    @DisplayName("GET /users/search with valid email returns UserResponse")
    void testSearchByEmailSuccess() {
        User otherUser = new User();
        otherUser.setName("Bob");
        otherUser.setEmail("bob@example.com");
        setId(otherUser, 2L);

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(otherUser));

        UserResponse response = userController.searchByEmail("bob@example.com");

        assertNotNull(response);
        assertEquals(2L, response.getId());
        assertEquals("Bob", response.getName());
        assertEquals("bob@example.com", response.getEmail());
    }

    @Test
    @DisplayName("GET /users/search for own email throws 400 Bad Request")
    void testSearchByEmailOwnAccount() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.searchByEmail("alice@example.com")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot search for your own account", ex.getReason());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("GET /users/search with blank email throws 400 Bad Request")
    void testSearchByEmailBlank() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.searchByEmail("   ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Email parameter is required", ex.getReason());
    }

    @Test
    @DisplayName("GET /users/search when user not found throws 404 Not Found")
    void testSearchByEmailNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.searchByEmail("unknown@example.com")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
    }

    @Test
    @DisplayName("POST /users/register delegates to UserService and returns UserResponse")
    void testRegisterSuccess() {
        com.recovertogether.backend.dto.RegisterRequest request = new com.recovertogether.backend.dto.RegisterRequest();
        request.setName("Charlie");
        request.setEmail("charlie@example.com");
        request.setPassword("SecurePass123!");

        User registered = new User();
        registered.setName("Charlie");
        registered.setEmail("charlie@example.com");
        setId(registered, 3L);

        when(userService.register(request)).thenReturn(registered);

        UserResponse response = userController.register(request);

        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals("Charlie", response.getName());
        assertEquals("charlie@example.com", response.getEmail());
        verify(userService).register(request);
    }

    @Test
    @DisplayName("GET /users/{id} returns UserResponse DTO")
    void testGetUserByIdReturnsDto() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));

        UserResponse response = userController.getUserById(1L);

        assertEquals(1L, response.getId());
        assertEquals("Alice", response.getName());
        assertEquals("alice@example.com", response.getEmail());
    }

    @Test
    @DisplayName("PUT /users/me updates the current user's profile with normalized email")
    void testUpdateMeNormalizesEmail() {
        UpdateProfileRequest request = new UpdateProfileRequest("Alice Prime", "  Alice@Example.COM  ");

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userController.updateCurrentUser(request);

        assertNotNull(response);
        assertEquals("Alice Prime", response.getName());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("alice@example.com", currentUser.getEmail());
        verify(userRepository).save(currentUser);
        verify(auditLogService).log(any(), eq(1L), eq("alice@example.com"), any());
    }

    @Test
    @DisplayName("PUT /users/me with duplicate email throws 400 and does not save")
    void testUpdateMeDuplicateEmail() {
        UpdateProfileRequest request = new UpdateProfileRequest("Alice", "taken@example.com");

        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.updateCurrentUser(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT /users/me updates and canonicalises the user's timezone")
    void testUpdateMeTimezone() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Alice", "alice@example.com", " Asia/Kolkata ");

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userController.updateCurrentUser(request);

        assertEquals("Asia/Kolkata", response.getTimezone());
        assertEquals("Asia/Kolkata", currentUser.getTimezone());
        verify(userRepository).save(currentUser);
    }

    @Test
    @DisplayName("PUT /users/me without a timezone preserves the existing timezone")
    void testUpdateMePreservesTimezoneWhenOmitted() {
        currentUser.setTimezone("America/New_York");
        UpdateProfileRequest request = new UpdateProfileRequest("Alice", "alice@example.com");

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userController.updateCurrentUser(request);

        assertEquals("America/New_York", response.getTimezone());
        assertEquals("America/New_York", currentUser.getTimezone());
    }

    @Test
    @DisplayName("PUT /users/me with an invalid timezone throws 400 and does not save")
    void testUpdateMeInvalidTimezone() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Alice", "alice@example.com", "Invalid/Zone");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.updateCurrentUser(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid timezone"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("DELETE /users/me deletes the current account and returns JSON message")
    void testDeleteMe() {
        Map<String, String> result = userController.deleteCurrentUser();

        assertEquals("Account deleted successfully", result.get("message"));
        verify(userRepository).delete(currentUser);
        verify(auditLogService).log(any(), eq(1L), eq("alice@example.com"), any());
    }
}
