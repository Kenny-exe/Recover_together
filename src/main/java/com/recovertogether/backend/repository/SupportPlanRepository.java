package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.SupportPlan;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SupportPlanRepository extends JpaRepository<SupportPlan, Long> {
    Optional<SupportPlan> findByUser(User user);
    boolean existsByUser(User user);
    void deleteByUser(User user);
}
