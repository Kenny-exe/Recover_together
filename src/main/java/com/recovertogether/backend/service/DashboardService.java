package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.*;
import org.springframework.stereotype.Service;
import com.recovertogether.backend.dto.PartnerStatusResponse;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService
{
    private final DailyCheckInService dailyCheckInService;
    private final PartnerRequestService partnerRequestService;
    private final MessageService messageService;
    private final PartnerCheckInService partnerCheckInService;

    public DashboardService(
            DailyCheckInService dailyCheckInService,
            PartnerRequestService partnerRequestService,
            MessageService messageService,
            PartnerCheckInService partnerCheckInService)
    {
        this.dailyCheckInService = dailyCheckInService;
        this.partnerRequestService = partnerRequestService;
        this.messageService = messageService;
        this.partnerCheckInService = partnerCheckInService;
    }

    public DashboardResponse getDashboard()
    {
        CheckInStatsResponse stats =
                dailyCheckInService.getStats();

        StreakResponse streak =
                dailyCheckInService.getStreak();

        PartnerSummaryResponse partnerSummary =
                partnerRequestService.getPartnerSummary();

        LastSeenResponse partnerLastSeen =
                partnerRequestService.getPartnerLastSeen();

        UnreadCountResponse unread =
                messageService.getReadCount();

        PartnerStatusResponse partnerStatus;
        try
        {
            partnerStatus=partnerCheckInService.getPartnerStatus();
        }
        catch (ResponseStatusException e)
        {
            partnerStatus=new PartnerStatusResponse(false,-1,null);
        }

        return new DashboardResponse(
                streak.getCurrentStreak(),
                streak.getBestStreak(),

                stats.getTotalCheckIns(),
                stats.getSuccessCount(),
                stats.getRelapseCount(),
                stats.getSuccessRate(),

                unread.getUnreadCount(),

                partnerSummary.getName(),
                partnerSummary.getCurrentStreak(),
                partnerSummary.getBestStreak(),

                partnerLastSeen.getLastSeen(),
                partnerStatus.isPartnerCheckedInToday(),
                partnerStatus.getDaysSinceLastCheckIn()
        );
    }
}