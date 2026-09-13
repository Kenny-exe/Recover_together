package com.recovertogether.backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class RecoveryPlanRequest {

    @NotBlank(message = "Recovery goal is required")
    private String goal;

    private List<String> copingStrategies = new ArrayList<>();
    private List<String> triggers = new ArrayList<>();
    private List<String> contacts = new ArrayList<>();

    public RecoveryPlanRequest() {
    }

    public RecoveryPlanRequest(String goal, List<String> copingStrategies, List<String> triggers, List<String> contacts) {
        this.goal = goal;
        this.copingStrategies = copingStrategies != null ? copingStrategies : new ArrayList<>();
        this.triggers = triggers != null ? triggers : new ArrayList<>();
        this.contacts = contacts != null ? contacts : new ArrayList<>();
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
        this.copingStrategies = copingStrategies != null ? copingStrategies : new ArrayList<>();
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<String> triggers) {
        this.triggers = triggers != null ? triggers : new ArrayList<>();
    }

    public List<String> getContacts() {
        return contacts;
    }

    public void setContacts(List<String> contacts) {
        this.contacts = contacts != null ? contacts : new ArrayList<>();
    }
}
