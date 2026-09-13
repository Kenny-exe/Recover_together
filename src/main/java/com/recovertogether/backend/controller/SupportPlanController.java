package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.PartnerSupportResponse;
import com.recovertogether.backend.dto.SupportPlanRequest;
import com.recovertogether.backend.dto.SupportPlanResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.SupportPlanService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/support-plan")
public class SupportPlanController {

    private final SupportPlanService supportPlanService;

    public SupportPlanController(SupportPlanService supportPlanService) {
        this.supportPlanService = supportPlanService;
    }

    @GetMapping
    public SupportPlanResponse getPlan() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return supportPlanService.getPlan(currentUser);
    }

    @PutMapping
    public SupportPlanResponse updatePlan(@Valid @RequestBody SupportPlanRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return supportPlanService.updatePlan(currentUser, request.getSupportActions());
    }

    @GetMapping("/partner")
    public PartnerSupportResponse getPartnerSupportInfo() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return supportPlanService.getPartnerSupportInfo(currentUser);
    }
}
