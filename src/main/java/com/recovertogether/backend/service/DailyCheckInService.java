package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.CheckInRequest;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.CheckInStatus;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

}
