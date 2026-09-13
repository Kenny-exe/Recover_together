package com.recovertogether.backend;

import com.recovertogether.backend.controller.RecoveryResourceController;
import com.recovertogether.backend.controller.SupportController;
import com.recovertogether.backend.dto.*;
import com.recovertogether.backend.entity.RecoveryResource;
import com.recovertogether.backend.entity.SupportRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.ResourceCategory;
import com.recovertogether.backend.repository.*;
import com.recovertogether.backend.service.AuditLogService;
import com.recovertogether.backend.service.NotificationService;
import com.recovertogether.backend.service.PartnerRequestService;
import com.recovertogether.backend.service.RecoveryResourceService;
import com.recovertogether.backend.service.SupportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryResourceTest {

    @Mock
    private RecoveryResourceRepository resourceRepository;

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PartnerRequestService partnerRequestService;

    @Mock
    private SupportRequestRepository supportRequestRepository;

    @Mock
    private AuditLogService auditLogService;

    private RecoveryResourceService resourceService;
    private RecoveryResourceController resourceController;
    private SupportService supportService;

    private User testUser;
    private User partnerUser;

    @BeforeEach
    void setUp() {
        resourceService = new RecoveryResourceService(resourceRepository);
        resourceController = new RecoveryResourceController(resourceService);
        supportService = new SupportService(
                partnerRequestRepository,
                messageRepository,
                notificationService,
                partnerRequestService,
                supportRequestRepository,
                resourceService,
                auditLogService
        );

        testUser = new User();
        testUser.setName("Alice");
        testUser.setEmail("alice@example.com");
        setId(testUser, 1L);

        partnerUser = new User();
        partnerUser.setName("Bob");
        partnerUser.setEmail("bob@example.com");
        setId(partnerUser, 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser, null, Collections.emptyList())
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

    private RecoveryResource createResource(Long id, String name, ResourceCategory category, boolean active) {
        RecoveryResource r = new RecoveryResource(
                name,
                "Description for " + name,
                category,
                "555-0100",
                "https://example.org/" + name.toLowerCase().replace(" ", "-"),
                "contact@" + name.toLowerCase().replace(" ", "") + ".org",
                "24/7",
                active
        );
        r.setId(id);
        return r;
    }

    @Test
    @DisplayName("1. Retrieve all active resources in alphabetical order")
    void testGetAllActiveResources() {
        RecoveryResource r1 = createResource(1L, "Alpha Group", ResourceCategory.SUPPORT_GROUP, true);
        RecoveryResource r2 = createResource(2L, "Beta Counseling", ResourceCategory.COUNSELING, true);

        when(resourceRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(r1, r2));

        List<RecoveryResourceResponse> results = resourceController.getActiveResources(null);

        assertEquals(2, results.size());
        assertEquals("Alpha Group", results.get(0).getName());
        assertEquals("Beta Counseling", results.get(1).getName());
        verify(resourceRepository).findByActiveTrueOrderByNameAsc();
    }

    @Test
    @DisplayName("2. Filter active resources by category")
    void testGetActiveResourcesByCategory() {
        RecoveryResource r1 = createResource(1L, "Crisis Help Center", ResourceCategory.CRISIS, true);

        when(resourceRepository.findByActiveTrueAndCategoryOrderByNameAsc(ResourceCategory.CRISIS))
                .thenReturn(List.of(r1));

        List<RecoveryResourceResponse> results = resourceController.getActiveResources(ResourceCategory.CRISIS);

        assertEquals(1, results.size());
        assertEquals(ResourceCategory.CRISIS, results.get(0).getCategory());
        assertEquals("Crisis Help Center", results.get(0).getName());
        verify(resourceRepository).findByActiveTrueAndCategoryOrderByNameAsc(ResourceCategory.CRISIS);
    }

    @Test
    @DisplayName("3. Retrieve active resource by ID")
    void testGetResourceByIdSuccess() {
        RecoveryResource r1 = createResource(1L, "Crisis Line", ResourceCategory.CRISIS, true);
        when(resourceRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(r1));

        RecoveryResourceResponse response = resourceController.getResourceById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Crisis Line", response.getName());
        assertEquals(ResourceCategory.CRISIS, response.getCategory());
        assertEquals("24/7", response.getOperatingHours());
    }

    @Test
    @DisplayName("4. Non-existent resource ID returns 404")
    void testGetResourceByIdNotFound() {
        when(resourceRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> resourceController.getResourceById(999L));
    }

    @Test
    @DisplayName("5. Inactive resource ID returns 404")
    void testGetInactiveResourceByIdNotFound() {
        when(resourceRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> resourceController.getResourceById(2L));
    }

    @Test
    @DisplayName("6. Service helper: create resource")
    void testCreateResourceService() {
        RecoveryResourceRequest req = new RecoveryResourceRequest(
                "New Support Center",
                "Provides peer support",
                ResourceCategory.SUPPORT_GROUP,
                "555-0101",
                "https://example.org/center",
                "info@example.org",
                "Mon-Fri 9-5",
                true
        );

        when(resourceRepository.save(any(RecoveryResource.class))).thenAnswer(inv -> {
            RecoveryResource saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        RecoveryResourceResponse res = resourceService.createResource(req);

        assertNotNull(res);
        assertEquals(10L, res.getId());
        assertEquals("New Support Center", res.getName());
        assertEquals(ResourceCategory.SUPPORT_GROUP, res.getCategory());
        assertTrue(res.isActive());
    }

    @Test
    @DisplayName("7. Service helper: update resource")
    void testUpdateResourceService() {
        RecoveryResource existing = createResource(1L, "Old Name", ResourceCategory.COUNSELING, true);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(resourceRepository.save(any(RecoveryResource.class))).thenAnswer(inv -> inv.getArgument(0));

        RecoveryResourceRequest updateReq = new RecoveryResourceRequest(
                "Updated Name",
                "Updated description",
                ResourceCategory.HEALTHCARE,
                "555-0199",
                "https://example.org/updated",
                "updated@example.org",
                "24/7",
                true
        );

        RecoveryResourceResponse res = resourceService.updateResource(1L, updateReq);

        assertEquals("Updated Name", res.getName());
        assertEquals(ResourceCategory.HEALTHCARE, res.getCategory());
        assertEquals("24/7", res.getOperatingHours());
    }

    @Test
    @DisplayName("8. Service helper: toggle resource activation status")
    void testSetResourceActiveToggle() {
        RecoveryResource resource = createResource(1L, "Temp Center", ResourceCategory.OTHER, true);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(RecoveryResource.class))).thenAnswer(inv -> inv.getArgument(0));

        RecoveryResourceResponse deactivated = resourceService.setResourceActive(1L, false);
        assertFalse(deactivated.isActive());

        RecoveryResourceResponse reactivated = resourceService.setResourceActive(1L, true);
        assertTrue(reactivated.isActive());
    }

    @Test
    @DisplayName("9. Service helper: delete resource")
    void testDeleteResourceService() {
        RecoveryResource resource = createResource(1L, "To Delete", ResourceCategory.OTHER, true);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        assertDoesNotThrow(() -> resourceService.deleteResource(1L));
        verify(resourceRepository).delete(resource);
    }

    @Test
    @DisplayName("10. Fallback resources: prioritizes CRISIS and HOTLINE categories")
    void testFallbackResourcesPrioritizesCrisisAndHotline() {
        RecoveryResource crisisRes = createResource(1L, "Crisis Line A", ResourceCategory.CRISIS, true);
        RecoveryResource hotlineRes = createResource(2L, "Hotline B", ResourceCategory.HOTLINE, true);

        when(resourceRepository.findByActiveTrueAndCategoryInOrderByNameAsc(
                List.of(ResourceCategory.CRISIS, ResourceCategory.HOTLINE)))
                .thenReturn(List.of(crisisRes, hotlineRes));

        List<String> fallbacks = resourceService.getFallbackResources(3);

        assertEquals(2, fallbacks.size());
        assertTrue(fallbacks.get(0).contains("[CRISIS]"));
        assertTrue(fallbacks.get(1).contains("[HOTLINE]"));
    }

    @Test
    @DisplayName("11. Fallback resources: fills remaining slots with other active categories up to limit")
    void testFallbackResourcesFillsRemainingSlots() {
        RecoveryResource crisisRes = createResource(1L, "Crisis Line A", ResourceCategory.CRISIS, true);
        RecoveryResource groupRes = createResource(2L, "Support Group C", ResourceCategory.SUPPORT_GROUP, true);
        RecoveryResource therapyRes = createResource(3L, "Therapy Clinic D", ResourceCategory.COUNSELING, true);

        when(resourceRepository.findByActiveTrueAndCategoryInOrderByNameAsc(
                List.of(ResourceCategory.CRISIS, ResourceCategory.HOTLINE)))
                .thenReturn(List.of(crisisRes));

        when(resourceRepository.findByActiveTrueAndCategoryNotInOrderByNameAsc(
                List.of(ResourceCategory.CRISIS, ResourceCategory.HOTLINE)))
                .thenReturn(List.of(groupRes, therapyRes));

        List<String> fallbacks = resourceService.getFallbackResources(3);

        assertEquals(3, fallbacks.size());
        assertTrue(fallbacks.get(0).contains("[CRISIS]"));
        assertTrue(fallbacks.get(1).contains("[SUPPORT_GROUP]"));
        assertTrue(fallbacks.get(2).contains("[COUNSELING]"));
    }

    @Test
    @DisplayName("12. Fallback resources: caps output at maximum 3 resources")
    void testFallbackResourcesCapsAtThree() {
        RecoveryResource r1 = createResource(1L, "Crisis 1", ResourceCategory.CRISIS, true);
        RecoveryResource r2 = createResource(2L, "Crisis 2", ResourceCategory.CRISIS, true);
        RecoveryResource r3 = createResource(3L, "Crisis 3", ResourceCategory.CRISIS, true);
        RecoveryResource r4 = createResource(4L, "Crisis 4", ResourceCategory.CRISIS, true);

        when(resourceRepository.findByActiveTrueAndCategoryInOrderByNameAsc(anyCollection()))
                .thenReturn(List.of(r1, r2, r3, r4));

        List<String> fallbacks = resourceService.getFallbackResources(5); // Requests 5, must cap at 3

        assertEquals(3, fallbacks.size());
    }

    @Test
    @DisplayName("13. Fallback resources: returns empty list when directory has no active resources")
    void testFallbackResourcesEmptyWhenNoneActive() {
        when(resourceRepository.findByActiveTrueAndCategoryInOrderByNameAsc(anyCollection()))
                .thenReturn(Collections.emptyList());
        when(resourceRepository.findByActiveTrueAndCategoryNotInOrderByNameAsc(anyCollection()))
                .thenReturn(Collections.emptyList());

        List<String> fallbacks = resourceService.getFallbackResources(3);

        assertNotNull(fallbacks);
        assertTrue(fallbacks.isEmpty());
    }

    @Test
    @DisplayName("14. Support request integration: unpaired user receives populated fallback resources")
    void testSupportRequestFallbackIntegration() {
        when(partnerRequestService.findPartnerUser(testUser)).thenReturn(Optional.empty());

        RecoveryResource crisisRes = createResource(1L, "Community Crisis Helpline", ResourceCategory.CRISIS, true);
        when(resourceRepository.findByActiveTrueAndCategoryInOrderByNameAsc(anyCollection()))
                .thenReturn(List.of(crisisRes));
        when(resourceRepository.findByActiveTrueAndCategoryNotInOrderByNameAsc(anyCollection()))
                .thenReturn(Collections.emptyList());

        SupportRequestResponse response = supportService.requestSupport("Need assistance");

        assertFalse(response.isPartnerNotified());
        assertNotNull(response.getFallbackResources());
        assertEquals(1, response.getFallbackResources().size());
        assertTrue(response.getFallbackResources().get(0).contains("Community Crisis Helpline"));
        verify(supportRequestRepository).save(any(SupportRequest.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("15. Support request integration: paired user notifies partner without fallback resources")
    void testSupportRequestWithPartnerIgnoresFallback() {
        when(partnerRequestService.findPartnerUser(testUser)).thenReturn(Optional.of(partnerUser));

        SupportRequestResponse response = supportService.requestSupport("Need assistance");

        assertTrue(response.isPartnerNotified());
        assertTrue(response.getFallbackResources().isEmpty());
        verify(notificationService).createNotification(
                partnerUser,
                NotificationType.SUPPORT_REQUEST,
                "Your partner may need some support right now."
        );
        verify(supportRequestRepository).save(any(SupportRequest.class));
    }
}
