package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.CheckInResponse;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PartnerCheckInService
{
    private final DailyCheckInRepository dailyCheckInRepository;
    private final PartnerRequestService partnerRequestService;

    public PartnerCheckInService(
            DailyCheckInRepository dailyCheckInRepository,
            PartnerRequestService partnerRequestService)
    {
        this.dailyCheckInRepository = dailyCheckInRepository;
        this.partnerRequestService = partnerRequestService;
    }

    public CheckInResponse getPartnerLatestCheckIn()
    {
        User partnerUser =
                partnerRequestService.getCurrentPartnerUser();

        DailyCheckIn checkIn =
                dailyCheckInRepository
                        .findTopByUserOrderByDateDesc(partnerUser)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Partner has no check-ins"
                                ));

        return new CheckInResponse(checkIn);
    }
    public List<CheckInResponse> getPartnerHistory(int limit)
    {
        User partnerUser =
                partnerRequestService.getCurrentPartnerUser();

        return dailyCheckInRepository
                .findByUserOrderByDateDesc(partnerUser)
                .stream()
                .limit(limit)
                .map(CheckInResponse::new)
                .toList();
    }
}
