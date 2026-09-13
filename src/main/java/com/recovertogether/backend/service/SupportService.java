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

import com.recovertogether.backend.dto.SupportRequestResponse;
import com.recovertogether.backend.entity.SupportRequest;
import com.recovertogether.backend.repository.SupportRequestRepository;

import com.recovertogether.backend.enums.AuditAction;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class SupportService
{
    private final PartnerRequestRepository partnerRequestRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;
    private final PartnerRequestService partnerRequestService;
    private final SupportRequestRepository supportRequestRepository;
    private final RecoveryResourceService recoveryResourceService;
    private final AuditLogService auditLogService;

    public SupportService(PartnerRequestRepository partnerRequestRepository,
                          MessageRepository messageRepository,
                          NotificationService notificationService,
                          PartnerRequestService partnerRequestService,
                          SupportRequestRepository supportRequestRepository,
                          RecoveryResourceService recoveryResourceService,
                          AuditLogService auditLogService)
    {
        this.messageRepository=messageRepository;
        this.partnerRequestRepository=partnerRequestRepository;
        this.notificationService=notificationService;
        this.partnerRequestService=partnerRequestService;
        this.supportRequestRepository=supportRequestRepository;
        this.recoveryResourceService=recoveryResourceService;
        this.auditLogService=auditLogService;
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
        auditLogService.log(AuditAction.SOS_TRIGGERED, currentUser.getId(), currentUser.getEmail(), null);
    }

    public SupportRequestResponse requestSupport(String note)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> partnerOpt = partnerRequestService.findPartnerUser(currentUser);

        SupportRequest supportRequest = new SupportRequest();
        supportRequest.setUser(currentUser);
        supportRequest.setNote(note);

        if (partnerOpt.isPresent())
        {
            User partner = partnerOpt.get();
            supportRequest.setPartner(partner);
            supportRequestRepository.save(supportRequest);

            notificationService.createNotification(
                    partner,
                    NotificationType.SUPPORT_REQUEST,
                    "Your partner may need some support right now."
            );

            auditLogService.log(AuditAction.SUPPORT_REQUEST_CREATED, currentUser.getId(), currentUser.getEmail(), "Partner paired");

            return new SupportRequestResponse(true, "Support request sent to your partner", Collections.emptyList());
        }
        else
        {
            supportRequest.setPartner(null);
            supportRequestRepository.save(supportRequest);

            List<String> fallbacks = recoveryResourceService.getFallbackResources(3);

            auditLogService.log(AuditAction.SUPPORT_REQUEST_CREATED, currentUser.getId(), currentUser.getEmail(), "Unpaired");

            return new SupportRequestResponse(
                    false,
                    "No partner is currently paired. Reaching out for support is a courageous step; consider connecting with a trusted person or support group.",
                    fallbacks != null ? fallbacks : Collections.emptyList()
            );
        }
    }
}
