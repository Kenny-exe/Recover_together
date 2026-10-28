package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.repository.NotificationRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class MissedCheckInScheduler
{
    /** Local hour after which a missing check-in is reported to the partner. */
    private static final int MISSED_CHECKIN_HOUR = 21;

    private final PartnerRequestRepository partnerRequestRepository;
    private final DailyCheckInRepository dailyCheckInRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public MissedCheckInScheduler(
            PartnerRequestRepository partnerRequestRepository,
            DailyCheckInRepository dailyCheckInRepository,
            NotificationService notificationService,
            NotificationRepository notificationRepository)
    {
        this.partnerRequestRepository=partnerRequestRepository;
        this.dailyCheckInRepository=dailyCheckInRepository;
        this.notificationService=notificationService;
        this.notificationRepository=notificationRepository;
    }

    /**
     * Runs every 15 minutes so every IANA offset (including :30 and :45
     * zones) passes through 21:00 local. Duplicate protection is per user-local
     * day, so repeated runs cannot send the same alert twice.
     */
    @Scheduled(cron = "${checkin.missed.cron:0 */15 * * * *}")
    public void checkMissedCheckIns()
    {
        checkMissedCheckIns(ZonedDateTime.now(ZoneOffset.UTC));
    }

    public void checkMissedCheckIns(ZonedDateTime now)
    {
        List<PartnerRequest> partnerships=partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED);

        for(PartnerRequest partnership: partnerships)
        {
            evaluateMissedCheckIn(partnership.getSender(), partnership.getReceiver(), now);
            evaluateMissedCheckIn(partnership.getReceiver(), partnership.getSender(), now);
        }
    }

    private void evaluateMissedCheckIn(User user, User partner, ZonedDateTime now)
    {
        ZonedDateTime userNow = now.withZoneSameInstant(UserTimeZone.of(user));

        if(userNow.getHour() != MISSED_CHECKIN_HOUR)
        {
            return;
        }

        LocalDate userToday = userNow.toLocalDate();

        if(dailyCheckInRepository.findByUserAndDate(user, userToday).isPresent())
        {
            return;
        }

        String message = user.getName() + " missed today's check-in";

        // Notification timestamps are server-local. Convert the start of the
        // user's local day into the server zone so an alert from a previous
        // local day never suppresses today's.
        LocalDateTime startOfUserDayOnServer =
                userToday.atStartOfDay(userNow.getZone())
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .minusSeconds(1);

        boolean alreadyExists =
                notificationRepository
                        .existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                                partner,
                                NotificationType.MISSED_CHECKIN,
                                message,
                                startOfUserDayOnServer
                        );

        if(!alreadyExists)
        {
            notificationService.createNotification(
                    partner,
                    NotificationType.MISSED_CHECKIN,
                    message
            );
        }
    }
}
