package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Resolves the IANA timezone a user's recovery day is measured in.
 *
 * <p>Users without a timezone (legacy rows or hand-built test users) fall back
 * to UTC. A missing or invalid stored value must never break check-ins or
 * schedulers, so reads always fall back instead of throwing; writes go
 * through {@link #normalize(String)} and reject invalid zones with a 400.</p>
 */
public final class UserTimeZone {

    public static final String DEFAULT_TIMEZONE = User.DEFAULT_TIMEZONE;

    private UserTimeZone() {
    }

    public static ZoneId of(User user) {
        return parse(user == null ? null : user.getTimezone());
    }

    public static ZoneId parse(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }

        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    /**
     * @return the canonical zone id, or null when no timezone was supplied
     * @throws ResponseStatusException 400 when the supplied zone is invalid
     */
    public static String normalize(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }

        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid timezone. Use an IANA timezone ID such as Asia/Kolkata");
        }
    }

    public static String normalizeOrDefault(String timezone) {
        String normalized = normalize(timezone);
        return normalized == null ? DEFAULT_TIMEZONE : normalized;
    }

    public static LocalDate dateIn(User user, Instant instant) {
        return LocalDate.ofInstant(instant, of(user));
    }

    public static LocalDate today(User user) {
        return dateIn(user, Instant.now());
    }

    public static LocalTime timeIn(User user, Instant instant) {
        return LocalTime.ofInstant(instant, of(user));
    }

    public static LocalTime now(User user) {
        return timeIn(user, Instant.now());
    }
}
