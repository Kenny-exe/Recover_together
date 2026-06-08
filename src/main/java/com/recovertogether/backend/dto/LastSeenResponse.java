package com.recovertogether.backend.dto;

import java.time.LocalDateTime;

public class LastSeenResponse
{
    private String name;
    private LocalDateTime lastSeen;

    public LastSeenResponse(String name, LocalDateTime lastSeen)
    {
        this.name=name;
        this.lastSeen=lastSeen;
    }

    public String getName(){return name;}
    public LocalDateTime getLastSeen(){return lastSeen;}
}
