package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.MessageResponse;
import com.recovertogether.backend.service.SupportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/support")
public class SupportController
{
    private final SupportService supportService;
    public SupportController(SupportService supportService)
    {
        this.supportService=supportService;
    }

    @PostMapping("/sos")
    public MessageResponse sendSOS()
    {
        supportService.sendSOS();
        return new MessageResponse("SOS alert sent to your partner");
    }
}
