package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.ChatMessageResponse;
import com.recovertogether.backend.dto.MessageRequest;
import com.recovertogether.backend.dto.MessageResponse;
import com.recovertogether.backend.dto.UnreadCountResponse;
import com.recovertogether.backend.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessageController
{
    private final MessageService messageService;

    public MessageController(MessageService messageService)
    {
        this.messageService = messageService;
    }

    @PostMapping("/send/{partnerId}")
    public MessageResponse sendMessage(
            @PathVariable Long partnerId,
            @Valid @RequestBody MessageRequest request)
    {
        messageService.sendMessage(
                partnerId,
                request.getContent()
        );

        return new MessageResponse("Message sent");
    }

    @GetMapping("/conversation/{partnerId}")
    public List<ChatMessageResponse> getConversation(
            @PathVariable Long partnerId, @RequestParam(defaultValue = "50") int limit)
    {
        return messageService.getConversation(partnerId,limit);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount()
    {
        return messageService.getReadCount();
    }
}