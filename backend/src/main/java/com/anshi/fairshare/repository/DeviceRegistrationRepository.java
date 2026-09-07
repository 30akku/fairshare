package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.DeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRegistrationRepository extends JpaRepository<DeviceRegistration, UUID> {
    Optional<DeviceRegistration> findByToken(String token);
    List<DeviceRegistration> findByUserIdIn(List<UUID> userIds);
    void deleteByUserId(UUID userId);
}

