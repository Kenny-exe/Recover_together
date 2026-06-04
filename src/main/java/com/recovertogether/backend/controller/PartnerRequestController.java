package com.recovertogether.backend.controller;
import com.recovertogether.backend.dto.PartnerRequestResponse;
import com.recovertogether.backend.service.PartnerRequestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/partner")
public class PartnerRequestController
{
    private final PartnerRequestService partnerRequestService;

    public PartnerRequestController(PartnerRequestService partnerRequestService)
    {
        this.partnerRequestService=partnerRequestService;
    }

    @PostMapping("/request/{receiverId}")
    public String sendRequest(@PathVariable Long receiverId)
    {
        partnerRequestService.sendRequest(receiverId);
        return "Partner request sent";
    }

    @PostMapping("/accept/{requestId}")
    public String acceptRequest(@PathVariable Long requestId)
    {
        partnerRequestService.acceptRequest(requestId);
        return "Partner request accepted";
    }

    @PostMapping("/reject/{requestId}")
    public String rejectRequest(@PathVariable Long requestId)
    {
        partnerRequestService.rejectRequest(requestId);
        return "Request rejected";
    }

    @GetMapping("/requests")
    public List<PartnerRequestResponse> getIncomingRequests()
    {
        return partnerRequestService.getIncomingRequests();
    }
}
