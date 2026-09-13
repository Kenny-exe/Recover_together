package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.SupportRequest;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    List<SupportRequest> findByUserOrderByCreatedAtDesc(User user);
    List<SupportRequest> findByPartnerOrderByCreatedAtDesc(User partner);
}
