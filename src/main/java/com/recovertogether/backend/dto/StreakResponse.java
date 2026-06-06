package com.recovertogether.backend.dto;

public class StreakResponse
{
    private int currentStreak;
    private int bestStreak;

    public StreakResponse(int currentStreak, int bestStreak)
    {
        this.currentStreak=currentStreak;
        this.bestStreak=bestStreak;
    }

    public int getCurrentStreak() {return currentStreak;}

    public int getBestStreak() {return bestStreak;}
}
