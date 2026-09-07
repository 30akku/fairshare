package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);
    Optional<GroupMember> findByGroupIdAndUserIdAndActiveTrue(UUID groupId, UUID userId);
    List<GroupMember> findByGroupIdAndActiveTrueOrderByJoinedAt(UUID groupId);
    boolean existsByGroupIdAndUserIdAndActiveTrue(UUID groupId, UUID userId);
    List<GroupMember> findByUserIdAndActiveTrue(UUID userId);
}
