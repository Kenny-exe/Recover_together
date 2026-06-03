package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.User;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>
{
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}