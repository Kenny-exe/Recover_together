package com.recovertogether.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RandomCheckInReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(RandomCheckInReminderScheduler.class);

    private final CheckInReminderService checkInReminderService;

    public RandomCheckInReminderScheduler(CheckInReminderService checkInReminderService) {
        this.checkInReminderService = checkInReminderService;
    }

    /**
     * Runs every 15 minutes around the clock. Each user's reminder is evaluated
     * against that user's own local time and reminder window, so server-local
     * 08:00-22:00 no longer applies globally.
     */
    @Scheduled(cron = "${checkin.reminder.cron:0 */15 * * * *}")
    public void runReminderCheck() {
        log.debug("Running per-user check-in reminder scheduler");
        checkInReminderService.processReminders();
    }
}
