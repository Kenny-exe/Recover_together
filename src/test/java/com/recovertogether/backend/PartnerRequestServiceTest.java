package com.recovertogether.backend;

import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.DailyCheckInService;
import com.recovertogether.backend.service.PartnerRequestService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerRequestServiceTest {

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DailyCheckInService dailyCheckInService;

    @Mock
    private com.recovertogether.backend.service.AuditLogService auditLogService;

    @InjectMocks
    private PartnerRequestService partnerRequestService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setName("Alice");
        sender.setEmail("alice@example.com");
        // set ID via reflection or mock
        setId(sender, 1L);

        receiver = new User();
        receiver.setName("Bob");
        receiver.setEmail("bob@example.com");
        setId(receiver, 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sender, null, Collections.emptyList())
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

    private void setRequestId(PartnerRequest request, Long id) {
        try {
            var field = PartnerRequest.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(request, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Cannot send request to oneself")
    void testSendRequestToSelf() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerRequestService.sendRequest(1L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("You cannot send a request to yourself", ex.getReason());
    }

    @Test
    @DisplayName("Sender already has active partner -> throws BAD_REQUEST")
    void testSenderAlreadyHasActivePartner() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(partnerRequestRepository.findFirstBySenderAndStatus(sender, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(new PartnerRequest()));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerRequestService.sendRequest(2L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("You already have an active partner", ex.getReason());
    }

    @Test
    @DisplayName("Receiver already has active partner -> throws BAD_REQUEST")
    void testReceiverAlreadyHasActivePartner() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        // Sender has no partner
        when(partnerRequestRepository.findFirstBySenderAndStatus(sender, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(sender, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.empty());
        // Receiver has partner
        when(partnerRequestRepository.findFirstBySenderAndStatus(receiver, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(new PartnerRequest()));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerRequestService.sendRequest(2L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("User already has an active partner", ex.getReason());
    }

    @Test
    @DisplayName("Re-requesting after REJECTED -> re-opens existing request to PENDING without duplicate key error")
    void testReRequestAfterRejected() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(partnerRequestRepository.findFirstBySenderAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED)))
                .thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED)))
                .thenReturn(Optional.empty());

        PartnerRequest rejectedRequest = new PartnerRequest();
        rejectedRequest.setSender(sender);
        rejectedRequest.setReceiver(receiver);
        rejectedRequest.setStatus(PartnerRequestStatus.REJECTED);

        when(partnerRequestRepository.findBySenderAndReceiver(sender, receiver))
                .thenReturn(Optional.of(rejectedRequest));

        partnerRequestService.sendRequest(2L);

        assertEquals(PartnerRequestStatus.PENDING, rejectedRequest.getStatus());
        verify(partnerRequestRepository).save(rejectedRequest);
        // Verify no NEW PartnerRequest was instantiated and saved
        verify(partnerRequestRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Accept request -> non-receiver gets 403 Forbidden")
    void testAcceptRequestNotReceiver() {
        User thirdParty = new User();
        thirdParty.setName("Charlie");
        thirdParty.setEmail("charlie@example.com");
        setId(thirdParty, 3L);

        PartnerRequest request = new PartnerRequest();
        setRequestId(request, 10L);
        request.setSender(thirdParty);
        request.setReceiver(receiver); // Receiver is Bob (2L), but logged in user is Alice (1L)
        request.setStatus(PartnerRequestStatus.PENDING);

        when(partnerRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerRequestService.acceptRequest(10L)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("Accept request -> receiver already has active partner throws BAD_REQUEST")
    void testAcceptRequestReceiverAlreadyHasPartner() {
        // Authenticated as Bob (2L)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(receiver, null, Collections.emptyList())
        );

        PartnerRequest request = new PartnerRequest();
        setRequestId(request, 10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(PartnerRequestStatus.PENDING);

        when(partnerRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(partnerRequestRepository.findFirstBySenderAndStatus(receiver, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(new PartnerRequest()));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerRequestService.acceptRequest(10L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("You already have an active partner", ex.getReason());
    }

    @Test
    @DisplayName("Accept request -> sender already has active partner throws BAD_REQUEST")
    void testAcceptRequestSenderAlreadyHasPartner() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(receiver, null, Collections.emptyList())
        );

        PartnerRequest request = new PartnerRequest();
        setRequestId(request, 10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(PartnerRequestStatus.PENDING);

        when(partnerRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        // Receiver has no partner
        when(partnerRequestRepository.findFirstBySenderAndStatus(receiver, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(receiver, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.empty());
        // Sender has partner
        when(partnerRequestRepository.findFirstBySenderAndStatus(sender, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(new PartnerRequest()));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerRequestService.acceptRequest(10L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Sender already has an active partner", ex.getReason());
    }

    @Test
    @DisplayName("Accept request -> successfully sets status to ACCEPTED")
    void testAcceptRequestSuccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(receiver, null, Collections.emptyList())
        );

        PartnerRequest request = new PartnerRequest();
        setRequestId(request, 10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(PartnerRequestStatus.PENDING);

        when(partnerRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(partnerRequestRepository.findFirstBySenderAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED)))
                .thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED)))
                .thenReturn(Optional.empty());

        partnerRequestService.acceptRequest(10L);

        assertEquals(PartnerRequestStatus.ACCEPTED, request.getStatus());
        verify(partnerRequestRepository).save(request);
    }

    @Test
    @DisplayName("findPartnerUser -> returns receiver when user is sender")
    void testFindPartnerUser_UserIsSender() {
        PartnerRequest request = new PartnerRequest();
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(PartnerRequestStatus.ACCEPTED);

        when(partnerRequestRepository.findFirstBySenderAndStatus(sender, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(request));

        Optional<User> partner = partnerRequestService.findPartnerUser(sender);

        assertTrue(partner.isPresent());
        assertEquals(receiver, partner.get());
    }

    @Test
    @DisplayName("findPartnerUser -> returns sender when user is receiver")
    void testFindPartnerUser_UserIsReceiver() {
        PartnerRequest request = new PartnerRequest();
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(PartnerRequestStatus.ACCEPTED);

        when(partnerRequestRepository.findFirstBySenderAndStatus(receiver, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(receiver, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(request));

        Optional<User> partner = partnerRequestService.findPartnerUser(receiver);

        assertTrue(partner.isPresent());
        assertEquals(sender, partner.get());
    }

    @Test
    @DisplayName("findPartnerUser -> returns empty when user has no partner")
    void testFindPartnerUser_NoPartner() {
        when(partnerRequestRepository.findFirstBySenderAndStatus(sender, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(sender, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.empty());

        Optional<User> partner = partnerRequestService.findPartnerUser(sender);

        assertTrue(partner.isEmpty());
    }
}
