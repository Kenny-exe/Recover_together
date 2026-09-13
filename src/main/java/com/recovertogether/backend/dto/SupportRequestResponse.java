package com.recovertogether.backend.dto;

import java.util.Collections;
import java.util.List;

public class SupportRequestResponse {

    private boolean partnerNotified;
    private String message;
    private List<String> fallbackResources;

    public SupportRequestResponse() {
    }

    public SupportRequestResponse(boolean partnerNotified, String message, List<String> fallbackResources) {
        this.partnerNotified = partnerNotified;
        this.message = message;
        this.fallbackResources = fallbackResources != null ? fallbackResources : Collections.emptyList();
    }

    public boolean isPartnerNotified() {
        return partnerNotified;
    }

    public void setPartnerNotified(boolean partnerNotified) {
        this.partnerNotified = partnerNotified;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getFallbackResources() {
        return fallbackResources;
    }

    public void setFallbackResources(List<String> fallbackResources) {
        this.fallbackResources = fallbackResources;
    }
}
