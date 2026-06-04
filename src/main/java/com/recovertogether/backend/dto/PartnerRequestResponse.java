package com.recovertogether.backend.dto;
import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.enums.PartnerRequestStatus;

import java.time.LocalDateTime;

public class PartnerRequestResponse
{
    private Long id;
    private String senderName;
    private String receiverName;
    private PartnerRequestStatus status;
    private LocalDateTime createdAt;

    public PartnerRequestResponse(PartnerRequest request)
    {
        this.id=request.getId();
        this.senderName=request.getSender().getName();
        this.receiverName=request.getReceiver().getName();
        this.status=request.getStatus();
        this.createdAt=request.getCreatedAt();
    }

    public Long getId(){ return id; }
    public String getSenderName(){ return senderName; }
    public String getReceiverName(){ return receiverName; }
    public PartnerRequestStatus getStatus(){ return status; }
    public LocalDateTime getCreatedAt() {return createdAt;}
}
