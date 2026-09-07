package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findByTokenHash(String tokenHash);
}

