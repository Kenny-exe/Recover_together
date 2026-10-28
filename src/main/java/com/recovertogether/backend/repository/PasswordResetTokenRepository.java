package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.PasswordResetToken;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findTopByUserOrderByCreatedAtDesc(User user);

    long countByUserAndCreatedAtAfter(User user, LocalDateTime cutoff);

    /**
     * Marks every still-usable code for the user as used. Called before
     * issuing a new code (only the latest code should work) and after a
     * successful reset.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    int invalidateAllForUser(@Param("user") User user);

    /**
     * Atomically consumes a reset token. Returns 0 when another request has
     * already used it, which closes the race between concurrent resets.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.id = :id AND t.used = false")
    int markUsed(@Param("id") Long id);
}
