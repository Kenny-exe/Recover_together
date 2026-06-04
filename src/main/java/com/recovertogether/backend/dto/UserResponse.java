package com.recovertogether.backend.dto;
import com.recovertogether.backend.entity.User;
import java.time.LocalDateTime;

public class UserResponse
{
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;

    public UserResponse(User user)
    {
        this.id= user.getId();
        this.name= user.getName();
        this.email= user.getEmail();
        this.createdAt=user.getCreatedAt();
    }

    public Long getId(){return id;}
    public String getName(){return name;}
    public String getEmail(){return email;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
