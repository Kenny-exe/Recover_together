package com.recovertogether.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.recovertogether.backend.controller.PartnerCheckInController;
import com.recovertogether.backend.dto.PartnerCheckInResponse;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.CheckInStatus;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.service.PartnerCheckInService;
import com.recovertogether.backend.service.PartnerRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerCheckInPrivacyTest {

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private PartnerRequestService partnerRequestService;

    @InjectMocks
    private PartnerCheckInService partnerCheckInService;

    private PartnerCheckInController partnerCheckInController;
    private ObjectMapper objectMapper;
    private User partner;

    @BeforeEach
    void setUp() {
        partnerCheckInController = new PartnerCheckInController(partnerCheckInService);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        partner = new User();
        partner.setName("Bob");
        partner.setEmail("bob@example.com");
        setId(partner, 2L);
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
    @DisplayName("CRITICAL PRIVACY: PartnerCheckInResponse DTO class has no 'note' field or accessor")
    void testPartnerCheckInResponseHasNoNoteField() {
        boolean hasNoteField = Arrays.stream(PartnerCheckInResponse.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equalsIgnoreCase("note"));
        assertFalse(hasNoteField, "PartnerCheckInResponse must NOT declare a 'note' field");

        boolean hasNoteGetter = Arrays.stream(PartnerCheckInResponse.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equalsIgnoreCase("getNote"));
        assertFalse(hasNoteGetter, "PartnerCheckInResponse must NOT declare a 'getNote' method");
    }

    @Test
    @DisplayName("CRITICAL PRIVACY: getPartnerLatestCheckIn strips private note from response")
    void testLatestCheckInStripsPrivateNote() throws Exception {
        DailyCheckIn privateCheckIn = new DailyCheckIn();
        setId(privateCheckIn, 100L);
        privateCheckIn.setUser(partner);
        privateCheckIn.setDate(LocalDate.now());
        privateCheckIn.setStatus(CheckInStatus.SUCCESS);
        privateCheckIn.setNote("Extremely sensitive personal recovery thoughts that should never be leaked to partner");
        privateCheckIn.setCreatedAt(LocalDateTime.now());

        when(partnerRequestService.getCurrentPartnerUser()).thenReturn(partner);
        when(dailyCheckInRepository.findTopByUserOrderByDateDesc(partner)).thenReturn(Optional.of(privateCheckIn));

        PartnerCheckInResponse response = partnerCheckInService.getPartnerLatestCheckIn();

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(LocalDate.now(), response.getDate());
        assertEquals(CheckInStatus.SUCCESS, response.getStatus());
        assertNotNull(response.getCreatedAt());

        // Verify JSON serialization contains no 'note' key or sensitive text
        String json = objectMapper.writeValueAsString(response);
        assertFalse(json.contains("note"), "JSON output must NOT contain 'note' property");
        assertFalse(json.contains("Extremely sensitive"), "JSON output must NOT leak private check-in content");
    }

    @Test
    @DisplayName("CRITICAL PRIVACY: getPartnerHistory strips private notes from all check-in records")
    void testPartnerHistoryStripsPrivateNotes() throws Exception {
        DailyCheckIn checkIn1 = new DailyCheckIn();
        setId(checkIn1, 101L);
        checkIn1.setUser(partner);
        checkIn1.setDate(LocalDate.now());
        checkIn1.setStatus(CheckInStatus.SUCCESS);
        checkIn1.setNote("Secret note day 1");
        checkIn1.setCreatedAt(LocalDateTime.now().minusDays(1));

        DailyCheckIn checkIn2 = new DailyCheckIn();
        setId(checkIn2, 102L);
        checkIn2.setUser(partner);
        checkIn2.setDate(LocalDate.now().minusDays(1));
        checkIn2.setStatus(CheckInStatus.STRUGGLING);
        checkIn2.setNote("Secret note day 2");
        checkIn2.setCreatedAt(LocalDateTime.now().minusDays(2));

        when(partnerRequestService.getCurrentPartnerUser()).thenReturn(partner);
        when(dailyCheckInRepository.findByUserOrderByDateDesc(partner)).thenReturn(List.of(checkIn1, checkIn2));

        List<PartnerCheckInResponse> history = partnerCheckInService.getPartnerHistory(30);

        assertEquals(2, history.size());
        assertEquals(101L, history.get(0).getId());
        assertEquals(CheckInStatus.SUCCESS, history.get(0).getStatus());
        assertEquals(102L, history.get(1).getId());
        assertEquals(CheckInStatus.STRUGGLING, history.get(1).getStatus());

        String json = objectMapper.writeValueAsString(history);
        assertFalse(json.contains("note"), "History JSON must not contain 'note' property");
        assertFalse(json.contains("Secret note"), "History JSON must not leak private notes");
    }

    @Test
    @DisplayName("getPartnerLatestCheckIn throws 404 when user has no partner")
    void testLatestCheckInNoPartnerThrowsNotFound() {
        when(partnerRequestService.getCurrentPartnerUser())
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No partner found"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerCheckInService.getPartnerLatestCheckIn()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("No partner found", ex.getReason());
        verify(dailyCheckInRepository, never()).findTopByUserOrderByDateDesc(any());
    }

    @Test
    @DisplayName("getPartnerHistory throws 404 when user has no partner")
    void testPartnerHistoryNoPartnerThrowsNotFound() {
        when(partnerRequestService.getCurrentPartnerUser())
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No partner found"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerCheckInService.getPartnerHistory(30)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("No partner found", ex.getReason());
        verify(dailyCheckInRepository, never()).findByUserOrderByDateDesc(any());
    }

    @Test
    @DisplayName("getPartnerLatestCheckIn throws 404 when partner has no check-ins")
    void testLatestCheckInPartnerHasNoCheckIns() {
        when(partnerRequestService.getCurrentPartnerUser()).thenReturn(partner);
        when(dailyCheckInRepository.findTopByUserOrderByDateDesc(partner)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> partnerCheckInService.getPartnerLatestCheckIn()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Partner has no check-ins", ex.getReason());
    }

    @Test
    @DisplayName("PartnerCheckInController delegates correctly and returns PartnerCheckInResponse")
    void testControllerDelegation() {
        DailyCheckIn checkIn = new DailyCheckIn();
        setId(checkIn, 200L);
        checkIn.setUser(partner);
        checkIn.setDate(LocalDate.now());
        checkIn.setStatus(CheckInStatus.SUCCESS);
        checkIn.setNote("Sensitive note");

        when(partnerRequestService.getCurrentPartnerUser()).thenReturn(partner);
        when(dailyCheckInRepository.findTopByUserOrderByDateDesc(partner)).thenReturn(Optional.of(checkIn));

        PartnerCheckInResponse response = partnerCheckInController.getPartnerLatestCheckIn();

        assertNotNull(response);
        assertEquals(200L, response.getId());
        assertEquals(CheckInStatus.SUCCESS, response.getStatus());
    }
}
