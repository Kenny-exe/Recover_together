package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.PartnerRequestResponse;
import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


@Service
public class PartnerRequestService
{
    public PartnerRequestService(
            PartnerRequestRepository partnerRequestRepository,
            UserRepository userRepository)
        {
            this.partnerRequestRepository = partnerRequestRepository;
            this.userRepository = userRepository;
        }

    private final PartnerRequestRepository partnerRequestRepository;
    private final UserRepository userRepository;

    public void sendRequest(Long receiverId)
    {

        User sender=(User) SecurityContextHolder.
                getContext().
                getAuthentication().
                getPrincipal();

        if(sender.getId().equals(receiverId))
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot send a request to yourself");
        }

        User receiver=userRepository.findById(receiverId).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));

        if(partnerRequestRepository.existsBySenderAndReceiverAndStatus(sender,receiver,PartnerRequestStatus.PENDING)
            || partnerRequestRepository.existsBySenderAndReceiverAndStatus(receiver,sender,PartnerRequestStatus.PENDING))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Request already exists");
        }


        PartnerRequest request = new PartnerRequest();
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(PartnerRequestStatus.PENDING);
        partnerRequestRepository.save(request);
    }

    public void acceptRequest(Long requestId)
    {
        User currentUser=(User) SecurityContextHolder.
                getContext().
                getAuthentication().
                getPrincipal();

        PartnerRequest request=partnerRequestRepository.findById(requestId).orElseThrow(()
                ->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found"));

        System.out.println("CURRENT USER ID: " + currentUser.getId());

        System.out.println("REQUEST RECEIVER ID: "
                + request.getReceiver().getId());

        System.out.println("REQUEST SENDER ID: "
                + request.getSender().getId());

        System.out.println("REQUEST STATUS: "
                + request.getStatus());

        if(!request.getReceiver().getId().equals(currentUser.getId()))
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot accept this request");
        }
        if(request.getStatus() != PartnerRequestStatus.PENDING)
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request already processed");
        }
        request.setStatus(PartnerRequestStatus.ACCEPTED);
        partnerRequestRepository.save(request);
    }

    public void rejectRequest(Long requestId)
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        PartnerRequest request=partnerRequestRepository.findById(requestId).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found"));

        System.out.println("CURRENT USER ID: " + currentUser.getId());
        System.out.println("RECEIVER ID: " + request.getReceiver().getId());
        System.out.println("SENDER ID: " + request.getSender().getId());

        if(!request.getReceiver().getId().equals(currentUser.getId()))
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot reject this request");
        }

        request.setStatus(PartnerRequestStatus.REJECTED);
        partnerRequestRepository.save(request);

    }

    public List<PartnerRequestResponse> getIncomingRequests()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return partnerRequestRepository.findByReceiverAndStatus(currentUser,
                PartnerRequestStatus.PENDING).
                stream().
                map(PartnerRequestResponse::new).
                toList();
    }
}
