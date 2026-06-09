package com.recovertogether.backend.dto;

import java.time.LocalDateTime;

public class DashboardResponse
{
    private int currentStreak;
    private int bestStreak;
    private long totalCheckIns;
    private long successCount;
    private long relapseCount;
    private double successRate;
    private long unreadMessages;
    private String partnerName;
    private int partnerCurrentStreak;
    private int partnerBestStreak;
    private LocalDateTime partnerLastSeen;
    private boolean partnerCheckedInToday;
    private long daysSinceLastCheckIn;
    private long achievementCount;

    public DashboardResponse(
            int currentStreak,
            int bestStreak,
            long totalCheckIns,
            long successCount,
            long relapseCount,
            double successRate,
            long unreadMessages,
            String partnerName,
            int partnerCurrentStreak,
            int partnerBestStreak,
            LocalDateTime partnerLastSeen,
            boolean partnerCheckedInToday,
            long daysSinceLastCheckIn,
            long achievementCount)
    {
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
        this.totalCheckIns = totalCheckIns;
        this.successCount = successCount;
        this.relapseCount = relapseCount;
        this.successRate = successRate;
        this.unreadMessages = unreadMessages;
        this.partnerName = partnerName;
        this.partnerCurrentStreak = partnerCurrentStreak;
        this.partnerBestStreak = partnerBestStreak;
        this.partnerLastSeen = partnerLastSeen;
        this.partnerCheckedInToday = partnerCheckedInToday;
        this.daysSinceLastCheckIn = daysSinceLastCheckIn;
        this.achievementCount = achievementCount;
    }

    public int getCurrentStreak(){ return currentStreak; }
    public int getBestStreak(){ return bestStreak; }
    public long getTotalCheckIns(){ return totalCheckIns; }
    public long getSuccessCount(){ return successCount; }
    public long getRelapseCount(){ return relapseCount; }
    public double getSuccessRate(){ return successRate; }
    public long getUnreadMessages(){ return unreadMessages; }
    public String getPartnerName(){ return partnerName; }
    public int getPartnerCurrentStreak(){ return partnerCurrentStreak; }
    public int getPartnerBestStreak(){ return partnerBestStreak; }
    public LocalDateTime getPartnerLastSeen() {return partnerLastSeen;}
    public boolean isPartnerCheckedInToday() {return partnerCheckedInToday;}
    public long getDaysSinceLastCheckIn() {return daysSinceLastCheckIn;}
    public long getAchievementCount() {return achievementCount;}
    public void setAchievementCount(long achievementCount) {this.achievementCount = achievementCount;}
}