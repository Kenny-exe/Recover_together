package com.recovertogether.backend.dto;
import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.enums.PartnerRequestStatus;

public class PartnerRequestResponse
{
    private Long id;
    private String senderName;
    private String receiverName;
    private PartnerRequestStatus status;

    public PartnerRequestResponse(PartnerRequest request)
    {
        this.id=request.getId();
        this.senderName=request.getSender().getName();
        this.receiverName=request.getReceiver().getName();
        this.status=request.getStatus();
    }

    public Long getId(){ return id; }
    public String getSenderName(){ return senderName; }
    public String getReceiverName(){ return receiverName; }
    public PartnerRequestStatus getStatus(){ return status; }
}
