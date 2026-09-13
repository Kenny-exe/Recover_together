package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.CheckInReminderSchedule;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface CheckInReminderScheduleRepository extends JpaRepository<CheckInReminderSchedule, Long> {

    Optional<CheckInReminderSchedule> findByUserAndDate(User user, LocalDate date);

    List<CheckInReminderSchedule> findByDateAndSentFalseAndScheduledTimeLessThanEqual(LocalDate date, LocalTime scheduledTime);

    boolean existsByUserAndDate(User user, LocalDate date);

    List<CheckInReminderSchedule> findByUserOrderByDateDesc(User user);
}
