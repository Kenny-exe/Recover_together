package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.RecoveryPlanRequest;
import com.recovertogether.backend.dto.RecoveryPlanResponse;
import com.recovertogether.backend.entity.RecoveryPlan;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.RecoveryPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecoveryPlanService {

    private final RecoveryPlanRepository recoveryPlanRepository;

    public RecoveryPlanService(RecoveryPlanRepository recoveryPlanRepository) {
        this.recoveryPlanRepository = recoveryPlanRepository;
    }

    public RecoveryPlanResponse getPlan(User user) {
        RecoveryPlan plan = recoveryPlanRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery plan not found"));
        return new RecoveryPlanResponse(plan);
    }

    @Transactional
    public RecoveryPlanResponse createPlan(User user, RecoveryPlanRequest request) {
        if (recoveryPlanRepository.existsByUser(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recovery plan already exists. Use PUT to update.");
        }
        RecoveryPlan plan = new RecoveryPlan(
                user,
                request.getGoal(),
                request.getCopingStrategies(),
                request.getTriggers(),
                request.getContacts()
        );
        RecoveryPlan saved = recoveryPlanRepository.save(plan);
        return new RecoveryPlanResponse(saved);
    }

    @Transactional
    public RecoveryPlanResponse updatePlan(User user, RecoveryPlanRequest request) {
        RecoveryPlan plan = recoveryPlanRepository.findByUser(user)
                .orElseGet(() -> new RecoveryPlan(user, request.getGoal(), request.getCopingStrategies(), request.getTriggers(), request.getContacts()));

        plan.setGoal(request.getGoal());
        plan.setCopingStrategies(request.getCopingStrategies());
        plan.setTriggers(request.getTriggers());
        plan.setContacts(request.getContacts());

        RecoveryPlan saved = recoveryPlanRepository.save(plan);
        return new RecoveryPlanResponse(saved);
    }

    @Transactional
    public void deletePlan(User user) {
        RecoveryPlan plan = recoveryPlanRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery plan not found"));
        recoveryPlanRepository.delete(plan);
    }
}
