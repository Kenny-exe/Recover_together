package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.Message;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

import java.util.List;

public interface MessageRepository
        extends JpaRepository<Message, Long>
{
    List<Message> findBySenderAndReceiverOrderByCreatedAtAsc(
            User sender,
            User receiver
    );

    List<Message> findByReceiverAndSenderOrderByCreatedAtAsc(
            User receiver,
            User sender
    );

    boolean existsBySenderAndSosAlertTrueAndCreatedAtAfter(User sender, LocalDateTime time);
    long countByReceiverAndReadFalse(User receiver);
}