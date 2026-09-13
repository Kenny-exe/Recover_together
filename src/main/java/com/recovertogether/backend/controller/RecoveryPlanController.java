package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.MessageResponse;
import com.recovertogether.backend.dto.RecoveryPlanRequest;
import com.recovertogether.backend.dto.RecoveryPlanResponse;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.RecoveryPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recovery-plan")
public class RecoveryPlanController {

    private final RecoveryPlanService recoveryPlanService;

    public RecoveryPlanController(RecoveryPlanService recoveryPlanService) {
        this.recoveryPlanService = recoveryPlanService;
    }

    @GetMapping
    public RecoveryPlanResponse getPlan() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return recoveryPlanService.getPlan(currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecoveryPlanResponse createPlan(@Valid @RequestBody RecoveryPlanRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return recoveryPlanService.createPlan(currentUser, request);
    }

    @PutMapping
    public RecoveryPlanResponse updatePlan(@Valid @RequestBody RecoveryPlanRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return recoveryPlanService.updatePlan(currentUser, request);
    }

    @DeleteMapping
    public MessageResponse deletePlan() {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        recoveryPlanService.deletePlan(currentUser);
        return new MessageResponse("Recovery plan deleted successfully");
    }
}
