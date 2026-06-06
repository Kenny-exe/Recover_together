package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.Message;

import java.time.LocalDateTime;

public class ChatMessageResponse
{
    private Long id;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;

    public ChatMessageResponse(Message message)
    {
        this.id = message.getId();
        this.senderName = message.getSender().getName();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
    }

    public Long getId()
    {
        return id;
    }

    public String getSenderName()
    {
        return senderName;
    }

    public String getContent()
    {
        return content;
    }

    public LocalDateTime getCreatedAt()
    {
        return createdAt;
    }
}