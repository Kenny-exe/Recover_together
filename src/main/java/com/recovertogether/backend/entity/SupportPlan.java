package com.recovertogether.backend.entity;

import com.recovertogether.backend.enums.SupportAction;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "support_plans")
public class SupportPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ElementCollection(targetClass = SupportAction.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "support_plan_actions", joinColumns = @JoinColumn(name = "plan_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private Set<SupportAction> supportActions = new HashSet<>();

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

    public SupportPlan() {
    }

    public SupportPlan(User user, Set<SupportAction> supportActions) {
        this.user = user;
        if (supportActions != null) {
            this.supportActions = new HashSet<>(supportActions);
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

    public Set<SupportAction> getSupportActions() {
        return supportActions;
    }

    public void setSupportActions(Set<SupportAction> supportActions) {
        this.supportActions = supportActions != null ? new HashSet<>(supportActions) : new HashSet<>();
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
