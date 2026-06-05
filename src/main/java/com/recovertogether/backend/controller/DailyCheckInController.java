package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.CheckInRequest;
import com.recovertogether.backend.dto.MessageResponse;
import com.recovertogether.backend.service.DailyCheckInService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkin")
public class DailyCheckInController
{
    private final DailyCheckInService dailyCheckInService;

    public DailyCheckInController(DailyCheckInService dailyCheckInService)
    {
        this.dailyCheckInService=dailyCheckInService;
    }

    @PostMapping
    public MessageResponse submitCheckIn(@RequestBody CheckInRequest request)
    {
        dailyCheckInService.submitCheckIn(request);
        return new MessageResponse("Check-in submitted successfully");
    }
}
