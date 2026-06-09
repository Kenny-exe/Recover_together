package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.Achievement;

import java.time.LocalDateTime;

public class AchievementResponse
{
    private String title;
    private LocalDateTime earnedAt;
    private long achievementCount;

    public AchievementResponse(Achievement achievement)
    {
        this.title= achievement.getTitle();
        this.earnedAt=achievement.getEarnedAt();
    }

    public String getTitle() {return title;}
    public LocalDateTime getEarnedAt() {return earnedAt;}
    public long getAchievementCount() {return achievementCount;}
    public void setAchievementCount(long achievementCount) {this.achievementCount = achievementCount;}
}
