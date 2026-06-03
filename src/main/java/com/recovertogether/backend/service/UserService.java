package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService
{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder)
    {
        this.userRepository=userRepository;
        this.passwordEncoder=passwordEncoder;
    }
    public User register(User user)
    {
        if(userRepository.existsByEmail(user.getEmail()))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email is already registered");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public String login(String email, String password)
    {
        User user= userRepository.findByEmail(email).orElseThrow(()->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));

        boolean matches= passwordEncoder.matches(password, user.getPassword());
        if(!matches)
        {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        }
        return "Login Successful";
    }

}