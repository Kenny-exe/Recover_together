package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.SupportAction;
import jakarta.validation.constraints.NotEmpty;
import java.util.HashSet;
import java.util.Set;

public class SupportPlanRequest {

    @NotEmpty(message = "At least one support action must be selected")
    private Set<SupportAction> supportActions = new HashSet<>();

    public SupportPlanRequest() {
    }

    public SupportPlanRequest(Set<SupportAction> supportActions) {
        this.supportActions = supportActions != null ? supportActions : new HashSet<>();
    }

    public Set<SupportAction> getSupportActions() {
        return supportActions;
    }

    public void setSupportActions(Set<SupportAction> supportActions) {
        this.supportActions = supportActions != null ? supportActions : new HashSet<>();
    }
}
