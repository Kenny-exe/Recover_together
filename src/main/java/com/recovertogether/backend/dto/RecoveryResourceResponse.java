package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.RecoveryResource;
import com.recovertogether.backend.enums.ResourceCategory;

public class RecoveryResourceResponse {

    private Long id;
    private String name;
    private String description;
    private ResourceCategory category;
    private String contactNumber;
    private String websiteUrl;
    private String email;
    private String operatingHours;
    private boolean active;

    public RecoveryResourceResponse() {
    }

    public RecoveryResourceResponse(RecoveryResource resource) {
        this.id = resource.getId();
        this.name = resource.getName();
        this.description = resource.getDescription();
        this.category = resource.getCategory();
        this.contactNumber = resource.getContactNumber();
        this.websiteUrl = resource.getWebsiteUrl();
        this.email = resource.getEmail();
        this.operatingHours = resource.getOperatingHours();
        this.active = resource.isActive();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ResourceCategory getCategory() {
        return category;
    }

    public void setCategory(ResourceCategory category) {
        this.category = category;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOperatingHours() {
        return operatingHours;
    }

    public void setOperatingHours(String operatingHours) {
        this.operatingHours = operatingHours;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
