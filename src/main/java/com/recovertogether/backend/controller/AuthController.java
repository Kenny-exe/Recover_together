package com.recovertogether.backend.controller;

import com.recovertogether.backend.dto.LoginRequest;
import com.recovertogether.backend.service.JwtService;
import com.recovertogether.backend.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController
{
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService)
    {
        this.userService=userService;
        this.jwtService=jwtService;
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest request)
    {
        userService.login(request.getEmail(),request.getPassword());
        return jwtService.generateToken(request.getEmail());
    }
}