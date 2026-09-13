package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.RecoveryResourceRequest;
import com.recovertogether.backend.dto.RecoveryResourceResponse;
import com.recovertogether.backend.entity.RecoveryResource;
import com.recovertogether.backend.enums.ResourceCategory;
import com.recovertogether.backend.repository.RecoveryResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecoveryResourceService {

    private final RecoveryResourceRepository resourceRepository;

    public RecoveryResourceService(RecoveryResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    public List<RecoveryResourceResponse> getActiveResources(ResourceCategory category) {
        List<RecoveryResource> resources;
        if (category == null) {
            resources = resourceRepository.findByActiveTrueOrderByNameAsc();
        } else {
            resources = resourceRepository.findByActiveTrueAndCategoryOrderByNameAsc(category);
        }
        return resources.stream().map(RecoveryResourceResponse::new).toList();
    }

    public RecoveryResourceResponse getResourceById(Long id) {
        return resourceRepository.findByIdAndActiveTrue(id)
                .map(RecoveryResourceResponse::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
    }

    public List<String> getFallbackResources(int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 3);
        List<String> results = new ArrayList<>();

        List<ResourceCategory> preferredCategories = List.of(ResourceCategory.CRISIS, ResourceCategory.HOTLINE);
        List<RecoveryResource> preferred = resourceRepository.findByActiveTrueAndCategoryInOrderByNameAsc(preferredCategories);

        for (RecoveryResource res : preferred) {
            if (results.size() >= effectiveLimit) {
                break;
            }
            results.add(formatResourceSummary(res));
        }

        if (results.size() < effectiveLimit) {
            List<RecoveryResource> others = resourceRepository.findByActiveTrueAndCategoryNotInOrderByNameAsc(preferredCategories);
            for (RecoveryResource res : others) {
                if (results.size() >= effectiveLimit) {
                    break;
                }
                results.add(formatResourceSummary(res));
            }
        }

        return results;
    }

    private String formatResourceSummary(RecoveryResource res) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(res.getCategory()).append("] ").append(res.getName());
        if (res.getDescription() != null && !res.getDescription().isBlank()) {
            sb.append(" - ").append(res.getDescription());
        }
        if (res.getContactNumber() != null && !res.getContactNumber().isBlank()) {
            sb.append(" (").append(res.getContactNumber()).append(")");
        }
        return sb.toString();
    }

    @Transactional
    public RecoveryResourceResponse createResource(RecoveryResourceRequest request) {
        RecoveryResource resource = new RecoveryResource(
                request.getName(),
                request.getDescription(),
                request.getCategory(),
                request.getContactNumber(),
                request.getWebsiteUrl(),
                request.getEmail(),
                request.getOperatingHours(),
                request.getActive() != null ? request.getActive() : true
        );
        RecoveryResource saved = resourceRepository.save(resource);
        return new RecoveryResourceResponse(saved);
    }

    @Transactional
    public RecoveryResourceResponse updateResource(Long id, RecoveryResourceRequest request) {
        RecoveryResource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));

        resource.setName(request.getName());
        resource.setDescription(request.getDescription());
        resource.setCategory(request.getCategory());
        resource.setContactNumber(request.getContactNumber());
        resource.setWebsiteUrl(request.getWebsiteUrl());
        resource.setEmail(request.getEmail());
        resource.setOperatingHours(request.getOperatingHours());
        if (request.getActive() != null) {
            resource.setActive(request.getActive());
        }

        RecoveryResource saved = resourceRepository.save(resource);
        return new RecoveryResourceResponse(saved);
    }

    @Transactional
    public RecoveryResourceResponse setResourceActive(Long id, boolean active) {
        RecoveryResource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        resource.setActive(active);
        RecoveryResource saved = resourceRepository.save(resource);
        return new RecoveryResourceResponse(saved);
    }

    @Transactional
    public void deleteResource(Long id) {
        RecoveryResource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        resourceRepository.delete(resource);
    }
}
