package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.ReminderWindowResponse;
import com.recovertogether.backend.entity.CheckInReminderSchedule;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.ReminderWindow;
import com.recovertogether.backend.repository.CheckInReminderScheduleRepository;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class CheckInReminderService {

    private static final Logger log = LoggerFactory.getLogger(CheckInReminderService.class);
    public static final String GENTLE_REMINDER_MESSAGE = "How are you doing today? Take a moment to check in.";

    private final UserRepository userRepository;
    private final CheckInReminderScheduleRepository reminderScheduleRepository;
    private final DailyCheckInRepository dailyCheckInRepository;
    private final NotificationService notificationService;
    private ReminderTimeGenerator timeGenerator;

    public CheckInReminderService(
            UserRepository userRepository,
            CheckInReminderScheduleRepository reminderScheduleRepository,
            DailyCheckInRepository dailyCheckInRepository,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.reminderScheduleRepository = reminderScheduleRepository;
        this.dailyCheckInRepository = dailyCheckInRepository;
        this.notificationService = notificationService;
        this.timeGenerator = ReminderTimeGenerator.defaultRandom();
    }

    public void setTimeGenerator(ReminderTimeGenerator timeGenerator) {
        this.timeGenerator = timeGenerator;
    }

    public ReminderWindowResponse getReminderWindow(User user) {
        return new ReminderWindowResponse(user.getReminderWindow());
    }

    @Transactional
    public ReminderWindowResponse updateReminderWindow(User user, ReminderWindow newWindow) {
        user.setReminderWindow(newWindow);
        User savedUser = userRepository.save(user);

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        Optional<CheckInReminderSchedule> existingOpt = reminderScheduleRepository.findByUserAndDate(savedUser, today);

        if (existingOpt.isPresent()) {
            CheckInReminderSchedule schedule = existingOpt.get();
            if (!schedule.isSent()) {
                // User changed window BEFORE today's reminder was sent: regenerate today's schedule for new window
                LocalTime newTime = timeGenerator.generateScheduledTime(newWindow, now);
                if (newTime != null) {
                    schedule.setScheduledTime(newTime);
                } else {
                    // New window has already elapsed for today; expire schedule to prevent firing outside window
                    schedule.setScheduledTime(newWindow.getEndTime());
                    schedule.setSent(true);
                }
                reminderScheduleRepository.save(schedule);
            }
            // If already sent, do not create another reminder today; new window takes effect on future days
        } else {
            generateScheduleForUser(savedUser, today, now);
        }

        return new ReminderWindowResponse(savedUser.getReminderWindow());
    }

    public void processReminders(LocalDate today, LocalTime currentTime) {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            try {
                generateScheduleForUser(user, today, currentTime);
            } catch (Exception e) {
                log.error("Failed to generate schedule for user {}: {}", user.getId(), e.getMessage());
            }
        }

        List<CheckInReminderSchedule> dueSchedules =
                reminderScheduleRepository.findByDateAndSentFalseAndScheduledTimeLessThanEqual(today, currentTime);

        for (CheckInReminderSchedule schedule : dueSchedules) {
            try {
                processDueSchedule(schedule, today);
            } catch (Exception e) {
                log.error("Failed to process due reminder schedule {}: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    public void generateScheduleForUser(User user, LocalDate date, LocalTime currentTime) {
        try {
            if (!reminderScheduleRepository.existsByUserAndDate(user, date)) {
                LocalTime scheduledTime = timeGenerator.generateScheduledTime(user.getReminderWindow(), currentTime);
                CheckInReminderSchedule schedule;
                if (scheduledTime != null) {
                    schedule = new CheckInReminderSchedule(user, date, scheduledTime);
                } else {
                    // Window already elapsed today; mark sent=true so it is not processed
                    schedule = new CheckInReminderSchedule(user, date, user.getReminderWindow().getEndTime());
                    schedule.setSent(true);
                }
                reminderScheduleRepository.save(schedule);
            }
        } catch (DataIntegrityViolationException e) {
            // Unique constraint uk_reminder_schedule_user_date caught concurrent schedule generation gracefully
            log.debug("Concurrent schedule creation avoided for user {} on date {}", user.getId(), date);
        }
    }

    private void processDueSchedule(CheckInReminderSchedule schedule, LocalDate date) {
        boolean alreadyCheckedIn = dailyCheckInRepository.findByUserAndDate(schedule.getUser(), date).isPresent();

        if (!alreadyCheckedIn) {
            notificationService.createNotification(
                    schedule.getUser(),
                    NotificationType.DAILY_CHECKIN_REMINDER,
                    GENTLE_REMINDER_MESSAGE
            );
        }

        schedule.setSent(true);
        reminderScheduleRepository.save(schedule);
    }
}
