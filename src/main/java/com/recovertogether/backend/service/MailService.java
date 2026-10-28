package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;

import java.time.Duration;

/**
 * Outbound transactional email. Implementations must never throw: email
 * delivery is best-effort and must not break the caller's workflow (the
 * reset token is already stored, so the user can request another code).
 */
public interface MailService {

    void sendPasswordResetCode(User user, String code, Duration validity);
}
