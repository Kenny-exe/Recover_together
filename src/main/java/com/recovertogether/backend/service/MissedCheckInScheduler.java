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
import java.util.List;

@Service
public class MissedCheckInScheduler
{
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

    @Scheduled(fixedRate = 60000)
    public void checkMissedCheckIns()
    {
        List<PartnerRequest> partnerships=partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED);

        for(PartnerRequest partnership: partnerships)
        {
            User sender=partnership.getSender();
            User receiver=partnership.getReceiver();

            boolean senderCheckedIn=dailyCheckInRepository.findByUserAndDate(sender, LocalDate.now()).isPresent();

            boolean receiverCheckedId=dailyCheckInRepository.findByUserAndDate(receiver, LocalDate.now()).isPresent();

            if(!senderCheckedIn)
            {
                String message =
                        sender.getName() + " missed today's check-in";

                boolean alreadyExists =
                        notificationRepository
                                .existsByReceiverAndTypeAndMessage(
                                        receiver,
                                        NotificationType.MISSED_CHECKIN,
                                        message
                                );

                if(!alreadyExists)
                {
                    notificationService.createNotification(
                            receiver,
                            NotificationType.MISSED_CHECKIN,
                            message
                    );
                }
            }
        }
    }
}
