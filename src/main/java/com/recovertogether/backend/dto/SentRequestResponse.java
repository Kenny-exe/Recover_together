package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.PartnerRequest;

public class SentRequestResponse
{
    private Long requestId;
    private String receiverName;

    public SentRequestResponse(PartnerRequest request)
    {
        this.requestId=request.getId();
        this.receiverName=request.getReceiver().getName();
    }

    public Long getRequestId()
    {
        return requestId;
    }

    public String getReceiverName()
    {
        return receiverName;
    }
}