package com.recovertogether.backend.dto;

public class CheckInStatsResponse
{
    private long totalCheckIns;
    private long successCount;
    private long relapseCount;
    private double successRate;
    private int currentStreak;
    private int bestStreak;

    public CheckInStatsResponse(
            long totalCheckIns,
            long successCount,
            long relapseCount,
            double successRate,
            int currentStreak,
            int bestStreak)
    {
        this.totalCheckIns = totalCheckIns;
        this.successCount = successCount;
        this.relapseCount = relapseCount;
        this.successRate = successRate;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
    }

    public long getTotalCheckIns() { return totalCheckIns; }

    public long getSuccessCount() { return successCount; }

    public long getRelapseCount() { return relapseCount; }

    public double getSuccessRate() { return successRate; }

    public int getCurrentStreak() { return currentStreak; }

    public int getBestStreak() { return bestStreak; }
}