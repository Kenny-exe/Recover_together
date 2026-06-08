package com.recovertogether.backend.controller;
import com.recovertogether.backend.dto.*;
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
    public MessageResponse sendRequest(@PathVariable Long receiverId)
    {
        partnerRequestService.sendRequest(receiverId);
        return new MessageResponse("Partner request sent");
    }

    @PostMapping("/accept/{requestId}")
    public MessageResponse acceptRequest(@PathVariable Long requestId)
    {
        partnerRequestService.acceptRequest(requestId);
        return new MessageResponse("Partner request accepted");
    }

    @PostMapping("/reject/{requestId}")
    public MessageResponse rejectRequest(@PathVariable Long requestId)
    {
        partnerRequestService.rejectRequest(requestId);
        return new MessageResponse("Request rejected");
    }

    @GetMapping("/requests")
    public List<PartnerRequestResponse> getIncomingRequests()
    {
        return partnerRequestService.getIncomingRequests();
    }

    @GetMapping("/sent")
    public List<SentRequestResponse> getSentRequests()
    {
        return partnerRequestService.getSentRequests();
    }

    @GetMapping("/current")
    public PartnerResponse getCurrentPartner()
    {
        return partnerRequestService.getCurrentPartner();
    }

    @GetMapping("/summary")
    public PartnerSummaryResponse getPartnerSummary()
    {
        return partnerRequestService.getPartnerSummary();
    }

    @PostMapping("/unpair")
    public MessageResponse unpair()
    {
        partnerRequestService.unpair(); return new MessageResponse("Partnership ended");
    }

    @GetMapping("/last-seen")
    public LastSeenResponse getPartnerLastSeen()
    {
        return partnerRequestService.getPartnerLastSeen();
    }
}
