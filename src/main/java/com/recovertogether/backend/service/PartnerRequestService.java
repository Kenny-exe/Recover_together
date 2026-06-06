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
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.enums.CheckInStatus;
import com.recovertogether.backend.repository.DailyCheckInRepository;

import java.util.List;

@Service
public class PartnerRequestService
{
    public PartnerRequestService(
            PartnerRequestRepository partnerRequestRepository,
            UserRepository userRepository, DailyCheckInRepository dailyCheckInRepository)
        {
            this.partnerRequestRepository = partnerRequestRepository;
            this.userRepository = userRepository;
            this.dailyCheckInRepository = dailyCheckInRepository;
        }

    private final PartnerRequestRepository partnerRequestRepository;
    private final UserRepository userRepository;
    private final DailyCheckInRepository dailyCheckInRepository;
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

        if(partnerRequestRepository.existsBySenderAndReceiverAndStatus(sender, receiver, PartnerRequestStatus.ACCEPTED)
            || partnerRequestRepository.existsBySenderAndReceiverAndStatus(receiver, sender, PartnerRequestStatus.ACCEPTED))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users are already partners");
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

        StreakResponse streak=calculateStreak(partnerUser);

        return new PartnerSummaryResponse(
                partner.getName(),
                partner.getEmail(),
                streak.getCurrentStreak(),
                streak.getBestStreak());
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

    private StreakResponse calculateStreak(User user)
    {
        List<DailyCheckIn> checkIns =
                dailyCheckInRepository.findByUserOrderByDateAsc(user);

        if(checkIns.isEmpty())
        {
            return new StreakResponse(0,0);
        }

        int currentRun = 0;
        int bestStreak = 0;

        java.time.LocalDate previousDate = null;

        for(DailyCheckIn checkIn : checkIns)
        {
            if(checkIn.getStatus() == CheckInStatus.SUCCESS)
            {
                if(previousDate == null)
                {
                    currentRun = 1;
                }
                else if(previousDate.plusDays(1).equals(checkIn.getDate()))
                {
                    currentRun++;
                }
                else
                {
                    currentRun = 1;
                }

                bestStreak = Math.max(bestStreak,currentRun);
                previousDate = checkIn.getDate();
            }
            else
            {
                currentRun = 0;
                previousDate = null;
            }
        }

        int currentStreak = 0;

        for(int i = checkIns.size()-1; i >= 0; i--)
        {
            DailyCheckIn checkIn = checkIns.get(i);

            if(checkIn.getStatus() == CheckInStatus.SUCCESS)
            {
                if(currentStreak == 0)
                {
                    currentStreak = 1;
                }
                else
                {
                    java.time.LocalDate currentDate =
                            checkIns.get(i+1).getDate();

                    if(checkIn.getDate().plusDays(1).equals(currentDate))
                    {
                        currentStreak++;
                    }
                    else
                    {
                        break;
                    }
                }
            }
            else
            {
                break;
            }
        }

        return new StreakResponse(
                currentStreak,
                bestStreak
        );
    }
}
