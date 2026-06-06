package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.*;
import com.recovertogether.backend.service.DailyCheckInService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/history")
    public List<CheckInResponse> getHistory(@RequestParam(defaultValue = "30") int limit)
    {
        return dailyCheckInService.getHistory(limit);
    }

    @GetMapping("/latest")
    public CheckInResponse getLatestCheckIn()
    {
        return dailyCheckInService.getLatestCheckIn();
    }

    @GetMapping("/streak")
    public StreakResponse getStreak()
    {
        return dailyCheckInService.getStreak();
    }

    @GetMapping("/stats")
    public CheckInStatsResponse getStats()
    {
        return dailyCheckInService.getStats();
    }
}
