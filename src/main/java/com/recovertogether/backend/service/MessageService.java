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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class MessageService
{
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final PartnerRequestRepository partnerRequestRepository;
    private final NotificationService notificationService;

    public MessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            PartnerRequestRepository partnerRequestRepository,
            NotificationService notificationService)
    {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.partnerRequestRepository = partnerRequestRepository;
        this.notificationService=notificationService;
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

        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);

        messageRepository.save(message);

        notificationService.createNotification(
                receiver,
                NotificationType.NEW_MESSAGE, sender.getName() + " sent you a message"
        );
    }
    public List<ChatMessageResponse> getConversation(Long partnerId, int limit)
    {
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

        List<Message> sentMessages =
                messageRepository
                        .findBySenderAndReceiverOrderByCreatedAtAsc(
                                currentUser,
                                partner
                        );

        List<Message> receivedMessages =
                messageRepository
                        .findByReceiverAndSenderOrderByCreatedAtAsc(
                                currentUser,
                                partner
                        );

        sentMessages.addAll(receivedMessages);

        sentMessages.sort(
                java.util.Comparator.comparing(
                        Message::getCreatedAt
                )
        );

        for(Message message:receivedMessages)
        {
            message.setRead(true);
        }
        messageRepository.saveAll(receivedMessages);

        if(sentMessages.size() > limit)
        {
            sentMessages =
                    sentMessages.subList(
                            sentMessages.size() - limit,
                            sentMessages.size()
                    );
        }

        return sentMessages
                .stream()
                .map(ChatMessageResponse::new)
                .toList();
    }

    public UnreadCountResponse getReadCount()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        long count=messageRepository.countByReceiverAndReadFalse(currentUser);
        return new UnreadCountResponse(count);
    }
}

