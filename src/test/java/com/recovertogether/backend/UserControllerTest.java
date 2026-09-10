package com.recovertogether.backend;

import com.recovertogether.backend.controller.UserController;
import com.recovertogether.backend.dto.UserResponse;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

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
        User updatedInput = new User();
        updatedInput.setName("Attacker");
        updatedInput.setEmail("attacker@example.com");

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
        User updatedInput = new User();
        updatedInput.setName("Alice Updated");
        updatedInput.setEmail("alice_new@example.com");

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
        User updatedInput = new User();
        updatedInput.setName("Alice");
        updatedInput.setEmail("existing@example.com");

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
    @DisplayName("GET /users returns UserResponse list and excludes current user")
    void testGetAllUsersExcludesCurrent() {
        User otherUser = new User();
        otherUser.setName("Bob");
        otherUser.setEmail("bob@example.com");
        setId(otherUser, 2L);

        when(userRepository.findAll()).thenReturn(List.of(currentUser, otherUser));

        List<UserResponse> users = userController.getAllUsers();

        assertEquals(1, users.size());
        assertEquals(2L, users.get(0).getId());
        assertEquals("Bob", users.get(0).getName());
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
}
