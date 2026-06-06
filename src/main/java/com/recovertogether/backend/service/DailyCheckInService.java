package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.*;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.CheckInStatus;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import java.time.LocalDate;

@Service
public class DailyCheckInService
{
    private final DailyCheckInRepository dailyCheckInRepository;
    private final UserRepository userRepository;

    public DailyCheckInService(DailyCheckInRepository dailyCheckInRepository, UserRepository userRepository)
    {
        this.dailyCheckInRepository=dailyCheckInRepository;
        this.userRepository=userRepository;
    }

    public void submitCheckIn(CheckInRequest request)
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if(dailyCheckInRepository.findByUserAndDate(currentUser, LocalDate.now()).isPresent())
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You have already checked in today"
            );
        }

        DailyCheckIn checkIn = new DailyCheckIn();

        checkIn.setUser(currentUser);
        checkIn.setDate(LocalDate.now());
        checkIn.setStatus(request.getStatus());
        checkIn.setNote(request.getNote());

        dailyCheckInRepository.save(checkIn);
    }

    public List<CheckInResponse> getHistory(int limit)
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return dailyCheckInRepository.findByUserOrderByDateDesc(currentUser).stream().limit(limit).map(CheckInResponse::new).toList();
    }

    public CheckInResponse getLatestCheckIn()
    {
        User currenrUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        DailyCheckIn checkIn= dailyCheckInRepository.findTopByUserOrderByDateDesc(currenrUser).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"No check-ins found"));

        return new CheckInResponse(checkIn);
    }

    public StreakResponse getStreak()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<DailyCheckIn> checkIns=dailyCheckInRepository.findByUserOrderByDateAsc(currentUser);

        if(checkIns.isEmpty())
        {
            return new StreakResponse(0,0);
        }

        int currentRun=0;
        int bestStreak=0;
        LocalDate previousDate=null;

        for(DailyCheckIn checkIn:checkIns)
        {
            if(checkIn.getStatus()==CheckInStatus.SUCCESS)
            {
                if(previousDate==null)
                {
                    currentRun=1;
                }
                else if(previousDate.plusDays(1).equals(checkIn.getDate()))
                {
                    currentRun++;
                }
                else
                {
                    currentRun=1;
                }

                bestStreak=Math.max(bestStreak,currentRun);
                previousDate=checkIn.getDate();
            }
            else
            {
                currentRun=0;
                previousDate=null;
            }
        }

        int currentStreak=0;

        for(int i=checkIns.size()-1;i>=0;i--)
        {
            DailyCheckIn checkIn = checkIns.get(i);

            if(checkIn.getStatus()==CheckInStatus.SUCCESS)
            {
                if(currentStreak==0)
                {
                    currentStreak=1;
                }
                else
                {
                    LocalDate currentDate = checkIns.get(i+1).getDate();
                    if(checkIn.getDate().plusDays(1).equals(currentDate))
                    {
                        currentStreak++;
                    }
                    else
                    {
                        break;
                    }
                }
            }
            else
            {
                break;
            }
        }
        return new StreakResponse(currentStreak,bestStreak);
    }

    public CheckInStatsResponse getStats()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        long totalCheckIns=dailyCheckInRepository.countByUser(currentUser);
        long successCount=dailyCheckInRepository.countByUserAndStatus(currentUser,CheckInStatus.SUCCESS);
        long relapseCount=dailyCheckInRepository.countByUserAndStatus(currentUser,CheckInStatus.RELAPSE);

        double successRate=0.0;

        if(totalCheckIns>0)
        {
            successRate=(double) (successCount/totalCheckIns)*100;
        }

        StreakResponse streak = getStreak();

        return new CheckInStatsResponse(totalCheckIns, successCount, relapseCount, successRate, streak.getCurrentStreak(),streak.getBestStreak());
    }

}
