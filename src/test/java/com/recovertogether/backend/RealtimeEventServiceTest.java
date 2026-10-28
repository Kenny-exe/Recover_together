package com.recovertogether.backend;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.RealtimeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeEventServiceTest {

    private RealtimeEventService realtimeEventService;
    private User alice;

    @BeforeEach
    void setUp() {
        realtimeEventService = new RealtimeEventService();

        alice = new User();
        alice.setName("Alice");
        alice.setEmail("alice@example.com");
        setId(alice, 1L);
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
    @DisplayName("SUBSCRIBE: registers a connection for the user only")
    void testSubscribeRegistersConnection() {
        SseEmitter emitter = realtimeEventService.subscribe(alice);

        assertNotNull(emitter);
        assertEquals(1, realtimeEventService.numberOfConnections(1L));
        assertEquals(0, realtimeEventService.numberOfConnections(2L));
    }

    @Test
    @DisplayName("UNSUBSCRIBE: removes the connection and cleans up empty user entries")
    void testUnsubscribe() {
        SseEmitter emitter = realtimeEventService.subscribe(alice);
        assertNotNull(emitter);

        realtimeEventService.unsubscribe(1L, emitter);
        assertEquals(0, realtimeEventService.numberOfConnections(1L));

        // Unsubscribing an unknown connection is a safe no-op.
        assertDoesNotThrow(() -> realtimeEventService.unsubscribe(1L, emitter));
        assertDoesNotThrow(() -> realtimeEventService.unsubscribe(999L, emitter));
    }

    @Test
    @DisplayName("PUBLISH: no-op for users without connections (never throws)")
    void testPublishWithoutConnections() {
        assertDoesNotThrow(() -> realtimeEventService.publish(1L, "notification", Map.of("message", "hi")));
        assertDoesNotThrow(() -> realtimeEventService.publish(null, "notification", "payload"));
    }

    @Test
    @DisplayName("PUBLISH: delivers to all live connections of the target user")
    void testPublishToAllConnections() {
        SseEmitter first = realtimeEventService.subscribe(alice);
        SseEmitter second = realtimeEventService.subscribe(alice);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(2, realtimeEventService.numberOfConnections(1L));

        assertDoesNotThrow(() ->
                realtimeEventService.publish(1L, "notification", Map.of("message", "hello")));

        // Publishing to another user must not touch Alice's connections.
        assertDoesNotThrow(() -> realtimeEventService.publish(2L, "notification", Map.of("message", "other")));
        assertEquals(2, realtimeEventService.numberOfConnections(1L));
    }

    @Test
    @DisplayName("HEARTBEAT: safe when idle and when connections exist")
    void testHeartbeatIsSafe() {
        assertDoesNotThrow(() -> realtimeEventService.heartbeat());

        SseEmitter emitter = realtimeEventService.subscribe(alice);
        assertNotNull(emitter);

        assertDoesNotThrow(() -> realtimeEventService.heartbeat());
    }
}
