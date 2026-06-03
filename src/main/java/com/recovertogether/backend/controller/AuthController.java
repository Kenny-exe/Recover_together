package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.LoginRequest;
import com.recovertogether.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController
{
    private final UserService userService;

    public AuthController(UserService userService)
    {
        this.userService=userService;
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request)
    {
        return userService.login(request.getEmail(),request.getPassword());
    }
}