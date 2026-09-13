package com.recovertogether.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recovery_plans")
public class RecoveryPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 500)
    private String goal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recovery_plan_coping_strategies", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "strategy", length = 255)
    private List<String> copingStrategies = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recovery_plan_triggers", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "trigger_item", length = 255)
    private List<String> triggers = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recovery_plan_contacts", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "contact", length = 255)
    private List<String> contacts = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public RecoveryPlan() {
    }

    public RecoveryPlan(User user, String goal, List<String> copingStrategies, List<String> triggers, List<String> contacts) {
        this.user = user;
        this.goal = goal;
        if (copingStrategies != null) {
            this.copingStrategies = new ArrayList<>(copingStrategies);
        }
        if (triggers != null) {
            this.triggers = new ArrayList<>(triggers);
        }
        if (contacts != null) {
            this.contacts = new ArrayList<>(contacts);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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
        this.copingStrategies = copingStrategies != null ? new ArrayList<>(copingStrategies) : new ArrayList<>();
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<String> triggers) {
        this.triggers = triggers != null ? new ArrayList<>(triggers) : new ArrayList<>();
    }

    public List<String> getContacts() {
        return contacts;
    }

    public void setContacts(List<String> contacts) {
        this.contacts = contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
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
