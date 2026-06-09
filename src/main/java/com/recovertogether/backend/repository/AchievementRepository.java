package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.Achievement;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AchievementRepository extends JpaRepository<Achievement, Long>
{
    List<Achievement> findByUserOrderByEarnedAtDesc(User user);
    boolean existsByUserAndTitle(User user, String title);
    long countByUser(User user);
}
