package com.recovertogether.backend.service;

import com.recovertogether.backend.enums.ReminderWindow;
import java.time.LocalTime;
import java.util.Random;

@FunctionalInterface
public interface ReminderTimeGenerator {

    LocalTime generateScheduledTime(ReminderWindow window, LocalTime earliestAllowed);

    static ReminderTimeGenerator defaultRandom() {
        return defaultRandom(new Random());
    }

    static ReminderTimeGenerator defaultRandom(Random random) {
        return (window, earliestAllowed) -> {
            LocalTime windowStart = window.getStartTime();
            LocalTime windowEnd = window.getEndTime();

            LocalTime effectiveStart = earliestAllowed.isAfter(windowStart) ? earliestAllowed : windowStart;

            if (!effectiveStart.isBefore(windowEnd)) {
                return null;
            }

            int startMinute = effectiveStart.getHour() * 60 + effectiveStart.getMinute();
            int endMinute = windowEnd.getHour() * 60 + windowEnd.getMinute();

            if (endMinute <= startMinute) {
                return effectiveStart;
            }

            int selectedMinute = startMinute + random.nextInt(endMinute - startMinute);
            return LocalTime.of(selectedMinute / 60, selectedMinute % 60);
        };
    }
}
