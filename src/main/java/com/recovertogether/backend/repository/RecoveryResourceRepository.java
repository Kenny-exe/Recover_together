package com.recovertogether.backend.repository;

import com.recovertogether.backend.entity.RecoveryResource;
import com.recovertogether.backend.enums.ResourceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecoveryResourceRepository extends JpaRepository<RecoveryResource, Long> {

    List<RecoveryResource> findByActiveTrueOrderByNameAsc();

    List<RecoveryResource> findByActiveTrueAndCategoryOrderByNameAsc(ResourceCategory category);

    Optional<RecoveryResource> findByIdAndActiveTrue(Long id);

    List<RecoveryResource> findByActiveTrueAndCategoryInOrderByNameAsc(Collection<ResourceCategory> categories);

    List<RecoveryResource> findByActiveTrueAndCategoryNotInOrderByNameAsc(Collection<ResourceCategory> categories);
}
