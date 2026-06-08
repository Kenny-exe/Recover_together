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

import java.time.LocalDateTime;

@Service
public class SupportService
{
    private final PartnerRequestRepository partnerRequestRepository;
    private final MessageRepository messageRepository;

    public SupportService(PartnerRequestRepository partnerRequestRepository, MessageRepository messageRepository)
    {
        this.messageRepository=messageRepository;
        this.partnerRequestRepository=partnerRequestRepository;
    }

    public void sendSOS()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        PartnerRequest request=partnerRequestRepository.findFirstBySenderAndStatus(currentUser,PartnerRequestStatus.ACCEPTED).orElseGet(()->
                partnerRequestRepository.findFirstByReceiverAndStatus(currentUser,PartnerRequestStatus.ACCEPTED).orElseThrow(()->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,"No partner found")));

        User partner;

        if(request.getSender().getId().equals(currentUser.getId()))
        {
            partner=request.getReceiver();
        }
        else
        {
            partner=request.getSender();
        }

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

    }
}
