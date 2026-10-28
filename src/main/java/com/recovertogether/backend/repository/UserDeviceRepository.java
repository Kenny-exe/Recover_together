package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long>
{
    List<UserDevice> findByUser(User user);

    List<UserDevice> findByUserAndActiveTrue(User user);

    Optional<UserDevice> findByIdAndUser(Long id, User user);

    Optional<UserDevice> findByToken(String token);
}
