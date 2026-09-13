package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.PartnerSupportResponse;
import com.recovertogether.backend.dto.SupportPlanResponse;
import com.recovertogether.backend.entity.RecoveryPlan;
import com.recovertogether.backend.entity.SupportPlan;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.SupportAction;
import com.recovertogether.backend.repository.RecoveryPlanRepository;
import com.recovertogether.backend.repository.SupportPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SupportPlanService {

    private final SupportPlanRepository supportPlanRepository;
    private final RecoveryPlanRepository recoveryPlanRepository;
    private final PartnerRequestService partnerRequestService;

    public SupportPlanService(
            SupportPlanRepository supportPlanRepository,
            RecoveryPlanRepository recoveryPlanRepository,
            PartnerRequestService partnerRequestService) {
        this.supportPlanRepository = supportPlanRepository;
        this.recoveryPlanRepository = recoveryPlanRepository;
        this.partnerRequestService = partnerRequestService;
    }

    public SupportPlanResponse getPlan(User user) {
        SupportPlan plan = supportPlanRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support plan not found"));
        return new SupportPlanResponse(plan.getSupportActions(), plan.getUpdatedAt());
    }

    @Transactional
    public SupportPlanResponse updatePlan(User user, Set<SupportAction> actions) {
        SupportPlan plan = supportPlanRepository.findByUser(user)
                .orElseGet(() -> new SupportPlan(user, actions));
        plan.setSupportActions(actions);
        SupportPlan saved = supportPlanRepository.save(plan);
        return new SupportPlanResponse(saved.getSupportActions(), saved.getUpdatedAt());
    }

    public PartnerSupportResponse getPartnerSupportInfo(User currentUser) {
        User partner = partnerRequestService.findPartnerUser(currentUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No partner found"));

        Optional<SupportPlan> planOpt = supportPlanRepository.findByUser(partner);
        Set<SupportAction> actions = planOpt.map(SupportPlan::getSupportActions).orElse(Collections.emptySet());

        String goal = null;
        List<String> copingStrategies = Collections.emptyList();

        if (actions.contains(SupportAction.REMIND_ME_OF_MY_GOAL) || actions.contains(SupportAction.REMIND_ME_OF_MY_COPING_PLAN)) {
            Optional<RecoveryPlan> recoveryPlanOpt = recoveryPlanRepository.findByUser(partner);
            if (recoveryPlanOpt.isPresent()) {
                RecoveryPlan recPlan = recoveryPlanOpt.get();
                if (actions.contains(SupportAction.REMIND_ME_OF_MY_GOAL)) {
                    goal = recPlan.getGoal();
                }
                if (actions.contains(SupportAction.REMIND_ME_OF_MY_COPING_PLAN)) {
                    copingStrategies = recPlan.getCopingStrategies();
                }
            }
        }

        return new PartnerSupportResponse(partner.getName(), actions, goal, copingStrategies);
    }
}
