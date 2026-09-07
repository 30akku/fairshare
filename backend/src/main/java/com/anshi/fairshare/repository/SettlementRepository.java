package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    Page<Settlement> findByGroupIdAndDeletedFalse(UUID groupId, Pageable pageable);
    List<Settlement> findByGroupIdAndDeletedFalse(UUID groupId);
    Optional<Settlement> findByIdAndDeletedFalse(UUID id);
}

