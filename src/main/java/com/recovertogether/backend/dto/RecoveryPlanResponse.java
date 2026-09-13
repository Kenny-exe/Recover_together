package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.RecoveryPlan;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RecoveryPlanResponse {

    private Long id;
    private String goal;
    private List<String> copingStrategies;
    private List<String> triggers;
    private List<String> contacts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RecoveryPlanResponse() {
    }

    public RecoveryPlanResponse(RecoveryPlan plan) {
        this.id = plan.getId();
        this.goal = plan.getGoal();
        this.copingStrategies = plan.getCopingStrategies() != null ? new ArrayList<>(plan.getCopingStrategies()) : new ArrayList<>();
        this.triggers = plan.getTriggers() != null ? new ArrayList<>(plan.getTriggers()) : new ArrayList<>();
        this.contacts = plan.getContacts() != null ? new ArrayList<>(plan.getContacts()) : new ArrayList<>();
        this.createdAt = plan.getCreatedAt();
        this.updatedAt = plan.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public List<String> getCopingStrategies() {
        return copingStrategies;
    }

    public void setCopingStrategies(List<String> copingStrategies) {
        this.copingStrategies = copingStrategies;
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<String> triggers) {
        this.triggers = triggers;
    }

    public List<String> getContacts() {
        return contacts;
    }

    public void setContacts(List<String> contacts) {
        this.contacts = contacts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
