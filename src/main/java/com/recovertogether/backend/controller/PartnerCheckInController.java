package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.CheckInResponse;
import com.recovertogether.backend.service.PartnerCheckInService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/partner/checkin")
public class PartnerCheckInController
{
    private final PartnerCheckInService partnerCheckInService;

    public PartnerCheckInController(
            PartnerCheckInService partnerCheckInService)
    {
        this.partnerCheckInService = partnerCheckInService;
    }

    @GetMapping("/latest")
    public CheckInResponse getPartnerLatestCheckIn()
    {
        return partnerCheckInService.getPartnerLatestCheckIn();
    }

    @GetMapping("/history")
    public List<CheckInResponse> getPartnerHistory(
            @RequestParam(defaultValue = "30") int limit)
    {
        return partnerCheckInService.getPartnerHistory(limit);
    }
}
