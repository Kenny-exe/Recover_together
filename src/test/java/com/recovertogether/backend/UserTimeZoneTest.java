package com.recovertogether.backend;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.UserTimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class UserTimeZoneTest {

    private static final Instant LATE_UTC = Instant.parse("2026-10-28T23:30:00Z");

    private User userWith(String timezone) {
        User user = new User();
        user.setTimezone(timezone);
        return user;
    }

    @Test
    @DisplayName("Missing, blank or invalid stored timezones fall back to UTC")
    void testFallbackToUtc() {
        assertEquals(ZoneId.of("UTC"), UserTimeZone.of(userWith(null)));
        assertEquals(ZoneId.of("UTC"), UserTimeZone.of(userWith("")));
        assertEquals(ZoneId.of("UTC"), UserTimeZone.of(userWith("  ")));
        assertEquals(ZoneId.of("UTC"), UserTimeZone.of(userWith("Not/AZone")));
        assertEquals(ZoneId.of("UTC"), UserTimeZone.of(null));
    }

    @Test
    @DisplayName("Valid IANA timezone is parsed, tolerating surrounding whitespace")
    void testParseValidTimezone() {
        assertEquals(ZoneId.of("Asia/Kolkata"), UserTimeZone.parse(" Asia/Kolkata "));
        assertEquals(ZoneId.of("America/New_York"), UserTimeZone.of(userWith("America/New_York")));
    }

    @Test
    @DisplayName("normalize canonicalises valid writes")
    void testNormalizeCanonicalises() {
        assertEquals("Asia/Kolkata", UserTimeZone.normalize(" Asia/Kolkata "));
        assertEquals("UTC", UserTimeZone.normalize("UTC"));
    }

    @Test
    @DisplayName("normalize returns null when no timezone is supplied")
    void testNormalizeEmpty() {
        assertNull(UserTimeZone.normalize(null));
        assertNull(UserTimeZone.normalize(""));
        assertNull(UserTimeZone.normalize("   "));
    }

    @Test
    @DisplayName("normalizeOrDefault defaults missing values to UTC")
    void testNormalizeOrDefault() {
        assertEquals("UTC", UserTimeZone.normalizeOrDefault(null));
        assertEquals("UTC", UserTimeZone.normalizeOrDefault("   "));
        assertEquals("Europe/Berlin", UserTimeZone.normalizeOrDefault("Europe/Berlin"));
    }

    @Test
    @DisplayName("normalize rejects invalid timezones with HTTP 400")
    void testNormalizeInvalidZone() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> UserTimeZone.normalize("Invalid/Zone"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid timezone"));
    }

    @Test
    @DisplayName("Local date and time are resolved in the user's timezone")
    void testDateAndTimeInUserZone() {
        User kolkataUser = userWith("Asia/Kolkata");

        assertEquals(LocalDate.of(2026, 10, 29), UserTimeZone.dateIn(kolkataUser, LATE_UTC));
        assertEquals(LocalTime.of(5, 0), UserTimeZone.timeIn(kolkataUser, LATE_UTC));

        User utcUser = userWith("UTC");
        assertEquals(LocalDate.of(2026, 10, 28), UserTimeZone.dateIn(utcUser, LATE_UTC));
        assertEquals(LocalTime.of(23, 30), UserTimeZone.timeIn(utcUser, LATE_UTC));
    }

    @Test
    @DisplayName("Null timezone user still resolves dates in UTC")
    void testDateInForUserWithoutTimezone() {
        assertEquals(LocalDate.of(2026, 10, 28),
                UserTimeZone.dateIn(userWith(null), LATE_UTC));
    }
}
