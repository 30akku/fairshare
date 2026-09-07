package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKeyAndOperation(UUID userId, String key, String operation);
}

