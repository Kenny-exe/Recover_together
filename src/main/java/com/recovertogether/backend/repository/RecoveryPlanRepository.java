package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.RecoveryPlan;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RecoveryPlanRepository extends JpaRepository<RecoveryPlan, Long> {
    Optional<RecoveryPlan> findByUser(User user);
    boolean existsByUser(User user);
    void deleteByUser(User user);
}
