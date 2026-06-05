package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCheckInRepository extends JpaRepository<DailyCheckIn,Long>
{
    List<DailyCheckIn> findByUser(User user);
    Optional<DailyCheckIn> findByUserAndDate(User user, LocalDate date);
}
