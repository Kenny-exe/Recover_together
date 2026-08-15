package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.Message;
import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.MessageRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.recovertogether.backend.enums.NotificationType;

import java.time.LocalDateTime;

@Service
public class SupportService
{
    private final PartnerRequestRepository partnerRequestRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;
    private final PartnerRequestService partnerRequestService;

    public SupportService(PartnerRequestRepository partnerRequestRepository,
                          MessageRepository messageRepository,
                          NotificationService notificationService,
                          PartnerRequestService partnerRequestService)
    {
        this.messageRepository=messageRepository;
        this.partnerRequestRepository=partnerRequestRepository;
        this.notificationService=notificationService;
        this.partnerRequestService=partnerRequestService;
    }

    public void sendSOS()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User partner = partnerRequestService.getPartner(currentUser);

        boolean recentSOS=messageRepository.existsBySenderAndSosAlertTrueAndCreatedAtAfter(currentUser, LocalDateTime.now().minusMinutes(1));
        if(recentSOS)
        {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait before sending another SOS");
        }


        Message sosMessage=new Message();
        sosMessage.setSender(currentUser);
        sosMessage.setReceiver(partner);
        sosMessage.setContent("SOS ALERT! YOUR PARTNER NEEDS HELP RIGHT NOW");
        sosMessage.setSosAlert(true);
        messageRepository.save(sosMessage);

        notificationService.createNotification(partner,NotificationType.SOS_ALERT, currentUser.getName()+" NEEDS SUPPORT IMMEDIATELY");

    }
}
