package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.AchievementResponse;
import com.recovertogether.backend.service.AchievementService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/achievements")
public class AchievementController
{
    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService)
    {
        this.achievementService=achievementService;
    }

    @GetMapping
    public List<AchievementResponse> getAchievements()
    {
        return achievementService.getAchievements();
    }
}
