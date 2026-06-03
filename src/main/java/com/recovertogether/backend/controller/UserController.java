package com.recovertogether.backend.controller;
import java.util.List;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    //CREATE
    @PostMapping("/register")
    public User register(@Valid @RequestBody User user) {

        return userRepository.save(user);
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
        userRepository.deleteById(id);
        return "User deleted successfully";
    }

    //UPDATE
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User updatedUser)
    {
        User existingUser = userRepository.findById(id).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));

        existingUser.setName(updatedUser.getName());
        existingUser.setEmail(updatedUser.getEmail());

        return userRepository.save(existingUser);
    }
}