package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.AppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {
    Page<AppNotification> findByUserId(UUID userId, Pageable pageable);
    Optional<AppNotification> findByIdAndUserId(UUID id, UUID userId);
}

