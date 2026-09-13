package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.SupportAction;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class SupportPlanResponse {

    private Set<SupportAction> supportActions;
    private LocalDateTime updatedAt;

    public SupportPlanResponse() {
    }

    public SupportPlanResponse(Set<SupportAction> supportActions, LocalDateTime updatedAt) {
        this.supportActions = supportActions != null ? new HashSet<>(supportActions) : new HashSet<>();
        this.updatedAt = updatedAt;
    }

    public Set<SupportAction> getSupportActions() {
        return supportActions;
    }

    public void setSupportActions(Set<SupportAction> supportActions) {
        this.supportActions = supportActions != null ? new HashSet<>(supportActions) : new HashSet<>();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
