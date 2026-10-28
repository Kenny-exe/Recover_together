package com.recovertogether.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovertogether.backend.controller.UserController;
import com.recovertogether.backend.dto.RegisterRequest;
import com.recovertogether.backend.dto.UserResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.enums.ReminderWindow;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.AuditLogService;
import com.recovertogether.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationAndPrivacyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    private UserService userService;
    private UserController userController;

    private User currentUser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, auditLogService);
        userController = new UserController(userRepository, userService, auditLogService);
        objectMapper = new ObjectMapper();

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

    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("REGISTRATION DTO: RegisterRequest only accepts name, email, password (no security fields)")
    void testRegisterRequestFieldsRestricted() {
        Field[] fields = RegisterRequest.class.getDeclaredFields();
        assertEquals(3, fields.length, "RegisterRequest must strictly contain only 3 fields");

        boolean hasFailedLoginAttempts = Arrays.stream(fields).anyMatch(f -> f.getName().equals("failedLoginAttempts"));
        boolean hasLockoutUntil = Arrays.stream(fields).anyMatch(f -> f.getName().equals("lockoutUntil"));
        boolean hasTokensInvalidatedBefore = Arrays.stream(fields).anyMatch(f -> f.getName().equals("tokensInvalidatedBefore"));

        assertFalse(hasFailedLoginAttempts, "RegisterRequest must NOT accept failedLoginAttempts");
        assertFalse(hasLockoutUntil, "RegisterRequest must NOT accept lockoutUntil");
        assertFalse(hasTokensInvalidatedBefore, "RegisterRequest must NOT accept tokensInvalidatedBefore");
    }

    @Test
    @DisplayName("REGISTRATION OVER-POSTING: Extra JSON properties are ignored by RegisterRequest")
    void testOverPostingIgnoredDuringDeserialization() throws Exception {
        String maliciousPayload = """
                {
                    "name": "Eve",
                    "email": "eve@example.com",
                    "password": "Password123!",
                    "failedLoginAttempts": 999,
                    "lockoutUntil": "2099-01-01T00:00:00",
                    "tokensInvalidatedBefore": "2099-01-01T00:00:00"
                }
                """;

        RegisterRequest request = objectMapper.readValue(maliciousPayload, RegisterRequest.class);
        assertEquals("Eve", request.getName());
        assertEquals("eve@example.com", request.getEmail());
        assertEquals("Password123!", request.getPassword());
    }

    @Test
    @DisplayName("REGISTRATION: Successfully registers user with default security attributes and BCrypt password")
    void testRegisterSuccessInitializesSecurityDefaults() {
        RegisterRequest request = new RegisterRequest("Bob", "bob@example.com", "SecretPass123!");

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecretPass123!")).thenReturn("$2a$10$encodedHash1234567890");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.prePersist();
            setId(u, 2L);
            return u;
        });

        UserResponse response = userController.register(request);

        assertNotNull(response);
        assertEquals(2L, response.getId());
        assertEquals("Bob", response.getName());
        assertEquals("bob@example.com", response.getEmail());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertEquals(0, savedUser.getFailedLoginAttempts(), "New user must have 0 failed login attempts");
        assertNull(savedUser.getLockoutUntil(), "New user must have null lockoutUntil");
        assertNull(savedUser.getTokensInvalidatedBefore(), "New user must have null tokensInvalidatedBefore");
        assertEquals(ReminderWindow.ANYTIME, savedUser.getReminderWindow());
        assertEquals("$2a$10$encodedHash1234567890", savedUser.getPassword());
        assertNotNull(savedUser.getCreatedAt());

        verify(auditLogService).log(eq(AuditAction.USER_REGISTERED), eq(2L), eq("bob@example.com"), isNull());
    }

    @Test
    @DisplayName("REGISTRATION: Duplicate email throws 400 Bad Request")
    void testRegisterDuplicateEmailThrows() {
        RegisterRequest request = new RegisterRequest("Bob", "bob@example.com", "SecretPass123!");

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.register(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Email is already registered", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("USER DIRECTORY PRIVACY: UserController has no getAllUsers endpoint (directory dump removed)")
    void testNoDirectoryDumpEndpoint() {
        Method[] methods = UserController.class.getDeclaredMethods();
        for (Method method : methods) {
            GetMapping getMapping = method.getAnnotation(GetMapping.class);
            if (getMapping != null) {
                String[] paths = getMapping.value();
                for (String path : paths) {
                    assertNotEquals("", path, "UserController must NOT map GET /users root endpoint");
                    assertNotEquals("/", path, "UserController must NOT map GET /users/ endpoint");
                }
            }
            assertNotEquals("getAllUsers", method.getName(), "UserController must NOT have a getAllUsers method");
        }
    }

    @Test
    @DisplayName("USER SEARCH: Exact email search returns minimal UserResponse (no sensitive security/recovery data)")
    void testSearchByEmailReturnsMinimalUserResponse() throws Exception {
        User target = new User();
        target.setName("Charlie");
        target.setEmail("charlie@example.com");
        target.setPassword("$2a$10$secretHash");
        setId(target, 5L);

        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(target));

        UserResponse result = userController.searchByEmail("  Charlie@Example.com  ");

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("Charlie", result.getName());
        assertEquals("charlie@example.com", result.getEmail());

        String json = objectMapper.writeValueAsString(result);
        assertFalse(json.contains("password"), "UserResponse must not expose password");
        assertFalse(json.contains("failedLoginAttempts"), "UserResponse must not expose failedLoginAttempts");
        assertFalse(json.contains("lockoutUntil"), "UserResponse must not expose lockoutUntil");
        assertFalse(json.contains("tokensInvalidatedBefore"), "UserResponse must not expose tokensInvalidatedBefore");
    }

    @Test
    @DisplayName("USER SEARCH: Searching for own account throws 400 Bad Request")
    void testSearchOwnAccountThrows() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.searchByEmail("alice@example.com")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot search for your own account", ex.getReason());
    }

    @Test
    @DisplayName("USER SEARCH: Searching with empty or blank email throws 400 Bad Request")
    void testSearchBlankEmailThrows() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.searchByEmail("   ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Email parameter is required", ex.getReason());
    }

    @Test
    @DisplayName("USER SEARCH: Searching for non-existent user throws 404 Not Found")
    void testSearchNotFoundThrows() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userController.searchByEmail("nonexistent@example.com")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
    }
}
