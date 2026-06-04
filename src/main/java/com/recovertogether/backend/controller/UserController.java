package com.recovertogether.backend.controller;
import java.util.List;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.dto.UserResponse;
import com.recovertogether.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.recovertogether.backend.entity.User;


@RestController
@RequestMapping("/users")
public class UserController
{
    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    //CREATE
    @PostMapping("/register")
    public User register(@Valid @RequestBody User user) {

        return userService.register(user);
    }

    //READ
    @GetMapping
    public List<User> getAllUsers()
    {
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id)
    {
        return userRepository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
    }

    //DELETE
    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id)
    {
        User user=userRepository.findById(id).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
        userRepository.delete(user);
        return "User deleted successfully";
    }

    //UPDATE
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @Valid @RequestBody User updatedUser)
    {
        User existingUser = userRepository.findById(id).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));

        existingUser.setName(updatedUser.getName());
        existingUser.setEmail(updatedUser.getEmail());

        return userRepository.save(existingUser);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser()
    {
        User user=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new UserResponse(user);
    }
}