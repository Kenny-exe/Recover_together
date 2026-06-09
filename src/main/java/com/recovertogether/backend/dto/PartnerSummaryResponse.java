package com.recovertogether.backend.dto;

import org.springframework.web.server.ResponseStatusException;

public class PartnerSummaryResponse
{
    private String name;
    private String email;
    private int currentStreak;
    private int bestStreak;

    public PartnerSummaryResponse(String name, String email, int currentStreak, int bestStreak)
    {
        this.name = name;
        this.email = email;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
    }

    public String getName() {return name;}

    public String getEmail() {return email;}

    public int getCurrentStreak() {return currentStreak;}

    public int getBestStreak() {return bestStreak;}
}