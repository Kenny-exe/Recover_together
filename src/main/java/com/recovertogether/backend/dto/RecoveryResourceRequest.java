package com.recovertogether.backend.dto;

import com.recovertogether.backend.enums.ResourceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RecoveryResourceRequest {

    @NotBlank(message = "Resource name is required")
    private String name;

    private String description;

    @NotNull(message = "Resource category is required")
    private ResourceCategory category;

    private String contactNumber;
    private String websiteUrl;
    private String email;
    private String operatingHours;
    private Boolean active = true;

    public RecoveryResourceRequest() {
    }

    public RecoveryResourceRequest(String name, String description, ResourceCategory category,
                                  String contactNumber, String websiteUrl, String email,
                                  String operatingHours, Boolean active) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.contactNumber = contactNumber;
        this.websiteUrl = websiteUrl;
        this.email = email;
        this.operatingHours = operatingHours;
        this.active = active != null ? active : true;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
