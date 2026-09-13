package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.RecoveryResourceResponse;
import com.recovertogether.backend.enums.ResourceCategory;
import com.recovertogether.backend.service.RecoveryResourceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/resources")
public class RecoveryResourceController {

    private final RecoveryResourceService resourceService;

    public RecoveryResourceController(RecoveryResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    public List<RecoveryResourceResponse> getActiveResources(@RequestParam(required = false) ResourceCategory category) {
        return resourceService.getActiveResources(category);
    }

    @GetMapping("/{id}")
    public RecoveryResourceResponse getResourceById(@PathVariable Long id) {
        return resourceService.getResourceById(id);
    }
}
