package com.recovertogether.backend.dto;

import com.recovertogether.backend.entity.User;

public class PartnerResponse
{
    private Long id;
    private String name;
    private String email;

    public PartnerResponse(User user)
    {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
    }

    public Long getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getEmail()
    {
        return email;
    }
}