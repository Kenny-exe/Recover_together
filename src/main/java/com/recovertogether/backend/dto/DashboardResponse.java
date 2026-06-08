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
            LocalDateTime partnerLastSeen)
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

    public LocalDateTime getPartnerLastSeen()
    {
        return partnerLastSeen;
    }
}