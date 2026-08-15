package com.recovertogether.backend.dto;

public class WeeklyReportResponse
{
    private long successDays;
    private long relapseDays;
    private double successRate;
    private int currentStreak;
    private int bestStreak;

    public WeeklyReportResponse(
            long successDays,
            long relapseDays,
            double successRate,
            int currentStreak,
            int bestStreak)
    {
        this.successDays = successDays;
        this.relapseDays = relapseDays;
        this.successRate = successRate;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
    }

    public long getSuccessDays() {return successDays;}

    public long getRelapseDays() {return relapseDays;}

    public double getSuccessRate() {return successRate;}

    public int getCurrentStreak() {return currentStreak;}

    public int getBestStreak() {return bestStreak;}
}