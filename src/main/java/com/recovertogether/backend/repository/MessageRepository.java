package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.Message;
import com.recovertogether.backend.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

import java.util.List;

public interface MessageRepository
        extends JpaRepository<Message, Long>
{
    /**
     * One page of a partner thread, newest first, restricted to messages with
     * {@code id < beforeIdExclusive}. The cursor must never be null (pass
     * {@code Long.MAX_VALUE} for the latest page); the service reverses the
     * result so the API keeps returning chronological (oldest-to-newest)
     * order. A composite index on each direction backs this query.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.id < :beforeIdExclusive
              AND ((m.sender = :me AND m.receiver = :partner)
                   OR (m.sender = :partner AND m.receiver = :me))
            """)
    List<Message> findConversationPage(
            @Param("me") User me,
            @Param("partner") User partner,
            @Param("beforeIdExclusive") Long beforeIdExclusive,
            Pageable pageable);

    /**
     * Marks every unread message received from the partner as read in a single
     * statement instead of loading and saving each row. Requires an active
     * transaction.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Message m SET m.read = true "
            + "WHERE m.read = false AND m.receiver = :receiver AND m.sender = :sender")
    int markReceivedFromSenderAsRead(
            @Param("receiver") User receiver,
            @Param("sender") User sender);

    boolean existsBySenderAndSosAlertTrueAndCreatedAtAfter(User sender, LocalDateTime time);

    long countByReceiverAndReadFalse(User receiver);
}
