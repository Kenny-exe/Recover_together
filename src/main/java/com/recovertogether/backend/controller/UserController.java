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
    public List<UserResponse> getAllUsers()
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .map(UserResponse::new)
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id)
    {
        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserResponse(user);
    }

    //DELETE
    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable Long id)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!currentUser.getId().equals(id))
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot delete another user's account");
        }

        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        userRepository.delete(user);
        return "User deleted successfully";
    }

    //UPDATE
    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody User updatedUser)
    {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!currentUser.getId().equals(id))
        {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify another user's account");
        }

        User existingUser = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!existingUser.getEmail().equalsIgnoreCase(updatedUser.getEmail())
                && userRepository.existsByEmail(updatedUser.getEmail()))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        existingUser.setName(updatedUser.getName());
        existingUser.setEmail(updatedUser.getEmail());

        User saved = userRepository.save(existingUser);
        return new UserResponse(saved);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser()
    {
        User user=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return new UserResponse(user);
    }
}