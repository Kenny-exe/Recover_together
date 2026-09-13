package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.SupportAction;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PartnerSupportResponse {

    private String partnerName;
    private Set<SupportAction> supportActions;
    private String goal;
    private List<String> copingStrategies;

    public PartnerSupportResponse() {
    }

    public PartnerSupportResponse(String partnerName, Set<SupportAction> supportActions, String goal, List<String> copingStrategies) {
        this.partnerName = partnerName;
        this.supportActions = supportActions != null ? supportActions : Collections.emptySet();
        this.goal = goal;
        this.copingStrategies = copingStrategies != null ? copingStrategies : Collections.emptyList();
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public Set<SupportAction> getSupportActions() {
        return supportActions;
    }

    public void setSupportActions(Set<SupportAction> supportActions) {
        this.supportActions = supportActions;
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
}
