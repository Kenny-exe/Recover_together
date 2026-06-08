package com.recovertogether.backend.dto;

import java.time.LocalDate;

public class PartnerStatusResponse
{
    private boolean partnerCheckedInToday;
    private long daysSinceLastCheckIn;
    private LocalDate lastCheckInDate;

    public PartnerStatusResponse(
            boolean partnerCheckedInToday,
            long daysSinceLastCheckIn,
            LocalDate lastCheckInDate)
    {
        this.partnerCheckedInToday = partnerCheckedInToday;
        this.daysSinceLastCheckIn = daysSinceLastCheckIn;
        this.lastCheckInDate = lastCheckInDate;
    }

    public boolean isPartnerCheckedInToday()
    {
        return partnerCheckedInToday;
    }

    public long getDaysSinceLastCheckIn()
    {
        return daysSinceLastCheckIn;
    }

    public LocalDate getLastCheckInDate()
    {
        return lastCheckInDate;
    }
}
