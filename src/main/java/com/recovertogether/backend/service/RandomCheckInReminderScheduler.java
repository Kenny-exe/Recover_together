package com.recovertogether.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class RandomCheckInReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(RandomCheckInReminderScheduler.class);

    private final CheckInReminderService checkInReminderService;

    public RandomCheckInReminderScheduler(CheckInReminderService checkInReminderService) {
        this.checkInReminderService = checkInReminderService;
    }

    @Scheduled(cron = "${checkin.reminder.cron:0 */15 8-22 * * *}")
    public void runReminderCheck() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        log.debug("Running random check-in reminder scheduler for date: {}, time: {}", today, now);
        checkInReminderService.processReminders(today, now);
    }
}
