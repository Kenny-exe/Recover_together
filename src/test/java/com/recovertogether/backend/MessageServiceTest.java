package com.recovertogether.backend;

import com.recovertogether.backend.dto.ChatMessageResponse;
import com.recovertogether.backend.entity.Message;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.MessageRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.MessageService;
import com.recovertogether.backend.service.NotificationService;
import com.recovertogether.backend.service.RealtimeEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RealtimeEventService realtimeEventService;

    private MessageService messageService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository, userRepository, partnerRequestRepository,
                notificationService, realtimeEventService);

        alice = new User();
        alice.setName("Alice");
        alice.setEmail("alice@example.com");
        setId(alice, 1L);

        bob = new User();
        bob.setName("Bob");
        bob.setEmail("bob@example.com");
        setId(bob, 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, Collections.emptyList())
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
    @DisplayName("SEND: stores message, forwards partnerId deep-link data, and emits SSE to the receiver")
    void testSendMessagePublishesRealtimeEvent() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(partnerRequestRepository.existsBySenderAndReceiverAndStatus(alice, bob, PartnerRequestStatus.ACCEPTED))
                .thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.sendMessage(2L, "hello Bob");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertSame(alice, messageCaptor.getValue().getSender());
        assertSame(bob, messageCaptor.getValue().getReceiver());
        assertEquals("hello Bob", messageCaptor.getValue().getContent());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).createNotification(
                eq(bob), eq(NotificationType.NEW_MESSAGE), contains("Alice"), dataCaptor.capture());
        assertEquals("1", dataCaptor.getValue().get("partnerId"));
        assertEquals("chat", com.recovertogether.backend.service.NotificationService
                .screenFor(NotificationType.NEW_MESSAGE));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(realtimeEventService).publish(eq(2L), eq("message"), payloadCaptor.capture());
        assertInstanceOf(ChatMessageResponse.class, payloadCaptor.getValue());
        assertEquals("hello Bob", ((ChatMessageResponse) payloadCaptor.getValue()).getContent());
    }

    @Test
    @DisplayName("SEND: non-partners are rejected and nothing is persisted or published")
    void testSendMessageToNonPartnerForbidden() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(partnerRequestRepository.existsBySenderAndReceiverAndStatus(alice, bob, PartnerRequestStatus.ACCEPTED))
                .thenReturn(false);
        when(partnerRequestRepository.existsBySenderAndReceiverAndStatus(bob, alice, PartnerRequestStatus.ACCEPTED))
                .thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> messageService.sendMessage(2L, "hello"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(notificationService, realtimeEventService);
    }

    @Test
    @DisplayName("SEND: blank message is rejected")
    void testSendBlankMessageRejected() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(partnerRequestRepository.existsBySenderAndReceiverAndStatus(alice, bob, PartnerRequestStatus.ACCEPTED))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> messageService.sendMessage(2L, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(notificationService, realtimeEventService);
    }
}
