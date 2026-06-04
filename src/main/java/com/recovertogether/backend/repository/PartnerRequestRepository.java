package com.recovertogether.backend.repository;
import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import com.recovertogether.backend.enums.PartnerRequestStatus;

import java.util.List;
import java.util.Optional;

public interface PartnerRequestRepository extends  JpaRepository<PartnerRequest, Long>
{
    List<PartnerRequest> findByReceiver(User receiver);
    List<PartnerRequest> findBySender(User sender);
    Optional<PartnerRequest> findById(Long id);
    boolean existsBySenderAndReceiverAndStatus(
            User sender,
            User receiver,
            PartnerRequestStatus status);
    List<PartnerRequest> findByReceiverAndStatus(
            User receiver,
            PartnerRequestStatus status);
    List<PartnerRequest> findBySenderAndStatus(
            User sender,
            PartnerRequestStatus status
    );

}
