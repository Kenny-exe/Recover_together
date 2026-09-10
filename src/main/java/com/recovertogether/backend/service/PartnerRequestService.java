package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.*;
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
import java.util.Optional;

@Service
public class PartnerRequestService
{
    public PartnerRequestService(
            PartnerRequestRepository partnerRequestRepository,
            UserRepository userRepository, DailyCheckInService dailyCheckInService)
            {
                this.partnerRequestRepository = partnerRequestRepository;
                this.userRepository = userRepository;
                this.dailyCheckInService = dailyCheckInService;
            }

    private final PartnerRequestRepository partnerRequestRepository;
    private final UserRepository userRepository;
    private final DailyCheckInService dailyCheckInService;

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

        if(hasActivePartner(sender))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already have an active partner");
        }

        if(hasActivePartner(receiver))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already has an active partner");
        }

        java.util.Optional<PartnerRequest> outgoing =
                partnerRequestRepository.findBySenderAndReceiver(sender, receiver);

        if(outgoing.isPresent())
        {
            PartnerRequest req = outgoing.get();
            if(req.getStatus() == PartnerRequestStatus.PENDING)
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request already exists");
            }
            if(req.getStatus() == PartnerRequestStatus.ACCEPTED)
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users are already partners");
            }
            if(req.getStatus() == PartnerRequestStatus.REJECTED)
            {
                req.setStatus(PartnerRequestStatus.PENDING);
                partnerRequestRepository.save(req);
                return;
            }
        }

        java.util.Optional<PartnerRequest> incoming =
                partnerRequestRepository.findBySenderAndReceiver(receiver, sender);

        if(incoming.isPresent())
        {
            PartnerRequest req = incoming.get();
            if(req.getStatus() == PartnerRequestStatus.PENDING)
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request already exists");
            }
            if(req.getStatus() == PartnerRequestStatus.ACCEPTED)
            {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users are already partners");
            }
            if(req.getStatus() == PartnerRequestStatus.REJECTED)
            {
                partnerRequestRepository.delete(req);
            }
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

        if(hasActivePartner(currentUser))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already have an active partner");
        }

        if(hasActivePartner(request.getSender()))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender already has an active partner");
        }

        request.setStatus(PartnerRequestStatus.ACCEPTED);
        partnerRequestRepository.save(request);
    }

    public void rejectRequest(Long requestId)
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        PartnerRequest request=partnerRequestRepository.findById(requestId).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found"));

        if(!request.getReceiver().getId().equals(currentUser.getId()))
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot reject this request");
        }
        if(request.getStatus() != PartnerRequestStatus.PENDING)
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request already processed"
            );
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

    public List<SentRequestResponse> getSentRequests()
    {
        User currentUser=
                (User) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return partnerRequestRepository
                .findBySenderAndStatus(
                        currentUser,
                        PartnerRequestStatus.PENDING
                )
                .stream()
                .map(SentRequestResponse::new)
                .toList();
    }

    public PartnerResponse getCurrentPartner()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        PartnerRequest request=partnerRequestRepository.findFirstBySenderAndStatus(currentUser,PartnerRequestStatus.ACCEPTED).
                orElseGet(()->
                        partnerRequestRepository.findFirstByReceiverAndStatus(currentUser,PartnerRequestStatus.ACCEPTED).
                                orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No partner found")));
        User partner;

        if(request.getSender().getId().equals(currentUser.getId()))
        {
            partner=request.getReceiver();
        }
        else
        {
            partner=request.getSender();
        }
        return new PartnerResponse(partner);
    }

    public PartnerSummaryResponse getPartnerSummary()
    {
        PartnerResponse partner = getCurrentPartner();
        User partnerUser=userRepository.findByEmail(partner.getEmail()).orElseThrow();

        StreakResponse streak =
                dailyCheckInService.calculateStreak(partnerUser);

        return new PartnerSummaryResponse(
                partner.getName(),
                partner.getEmail(),
                streak.getCurrentStreak(),
                streak.getBestStreak()
        );
    }

    public void unpair()
    {
        User currentUser =
                (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        PartnerRequest request =
                partnerRequestRepository.findFirstBySenderAndStatus(currentUser, PartnerRequestStatus.ACCEPTED)
                        .orElseGet(() -> partnerRequestRepository.findFirstByReceiverAndStatus(currentUser, PartnerRequestStatus.ACCEPTED)
                                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No partner found"))
                        );

        partnerRequestRepository.delete(request);
    }

    public LastSeenResponse getPartnerLastSeen()
    {
        User partnerUser = getCurrentPartnerUser();

        return new LastSeenResponse(partnerUser.getName(), partnerUser.getLastSeen());
    }

    public Optional<User> findPartnerUser(User currentUser)
    {
        Optional<PartnerRequest> asSender =
                partnerRequestRepository.findFirstBySenderAndStatus(currentUser, PartnerRequestStatus.ACCEPTED);
        if (asSender.isPresent())
        {
            return Optional.ofNullable(asSender.get().getReceiver());
        }

        Optional<PartnerRequest> asReceiver =
                partnerRequestRepository.findFirstByReceiverAndStatus(currentUser, PartnerRequestStatus.ACCEPTED);
        if (asReceiver.isPresent())
        {
            return Optional.ofNullable(asReceiver.get().getSender());
        }

        return Optional.empty();
    }

    public User getCurrentPartnerUser()
    {
        User currentUser =
                (User) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return getPartner(currentUser);
    }

    public User getPartner(User currentUser)
    {
        return findPartnerUser(currentUser)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No partner found"
                        )
                );
    }

    public boolean hasActivePartner(User user)
    {
        return partnerRequestRepository
                .findFirstBySenderAndStatus(user, PartnerRequestStatus.ACCEPTED)
                .isPresent()
            || partnerRequestRepository
                .findFirstByReceiverAndStatus(user, PartnerRequestStatus.ACCEPTED)
                .isPresent();
    }
}
