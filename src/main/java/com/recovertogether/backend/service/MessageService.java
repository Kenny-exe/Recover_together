package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.ChatMessageResponse;
import com.recovertogether.backend.dto.UnreadCountResponse;
import com.recovertogether.backend.entity.Message;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.MessageRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;

@Service
public class MessageService
{
    private static final int MAX_CONVERSATION_PAGE_SIZE = 200;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final PartnerRequestRepository partnerRequestRepository;
    private final NotificationService notificationService;
    private final RealtimeEventService realtimeEventService;

    public MessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            PartnerRequestRepository partnerRequestRepository,
            NotificationService notificationService,
            RealtimeEventService realtimeEventService)
    {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.partnerRequestRepository = partnerRequestRepository;
        this.notificationService=notificationService;
        this.realtimeEventService=realtimeEventService;
    }

    public void sendMessage(Long receiverId, String content)
    {
        User sender=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User receiver=userRepository.findById(receiverId).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));

        if(sender.getId().equals(receiverId))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You cannot message yourself");
        }

        boolean arePartners=partnerRequestRepository.
                existsBySenderAndReceiverAndStatus(sender,receiver,PartnerRequestStatus.ACCEPTED)
                ||
                partnerRequestRepository.
                        existsBySenderAndReceiverAndStatus(receiver,sender,PartnerRequestStatus.ACCEPTED);

        if(!arePartners)
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You can only message your partner");
        }


        Message message=new Message();

        content = content.trim();

        if(content.trim().isEmpty())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Message cannot be empty");
        }
        if(content.length() > 1000)
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message too long"
            );
        }

        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);

        messageRepository.save(message);

        notificationService.createNotification(
                receiver,
                NotificationType.NEW_MESSAGE,
                sender.getName() + " sent you a message",
                java.util.Map.of(
                        "partnerId", sender.getId().toString(),
                        "partnerName", sender.getName()
                )
        );

        // Foreground live delivery for open chat screens.
        try
        {
            realtimeEventService.publish(receiver.getId(), "message", new ChatMessageResponse(message));
        }
        catch (Exception e)
        {
            // Realtime delivery is best-effort; the message is already stored.
        }
    }
    /**
     * Returns a bounded page of the partner thread, oldest-to-newest.
     *
     * <p>Pagination is done by the database: a single cursor query on
     * {@code id} (strictly older than {@code beforeId}, newest window first)
     * is fetched in descending order and reversed for the response. The
     * caller's unread messages from this partner are marked read in the same
     * transaction with one bulk update.
     *
     * @param limit    page size, clamped to 1..200
     * @param beforeId exclusive cursor; null returns the latest window
     */
    @Transactional
    public List<ChatMessageResponse> getConversation(Long partnerId, int limit, Long beforeId)
    {
        if (limit <= 0)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be at least 1");
        }
        int pageSize = Math.min(limit, MAX_CONVERSATION_PAGE_SIZE);

        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User partner=userRepository.findById(partnerId).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));

        boolean arePartners=partnerRequestRepository
                .existsBySenderAndReceiverAndStatus(currentUser, partner, PartnerRequestStatus.ACCEPTED)
                ||
                partnerRequestRepository
                        .existsBySenderAndReceiverAndStatus(partner, currentUser, PartnerRequestStatus.ACCEPTED);

        if(!arePartners)
        {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only view conversations with your partner"
            );
        }

        // Opening the thread reads the conversation: the reader's unread
        // messages from this partner become read in one bulk statement.
        messageRepository.markReceivedFromSenderAsRead(currentUser, partner);

        List<Message> window = messageRepository.findConversationPage(
                currentUser,
                partner,
                beforeId == null ? Long.MAX_VALUE : beforeId,
                PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "id")));

        List<Message> chronological = new ArrayList<>(window);
        java.util.Collections.reverse(chronological);

        return chronological
                .stream()
                .map(ChatMessageResponse::new)
                .toList();
    }

    public List<ChatMessageResponse> getConversation(Long partnerId, int limit)
    {
        return getConversation(partnerId, limit, null);
    }

    public UnreadCountResponse getUnreadCount()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        long count=messageRepository.countByReceiverAndReadFalse(currentUser);
        return new UnreadCountResponse(count);
    }
}

