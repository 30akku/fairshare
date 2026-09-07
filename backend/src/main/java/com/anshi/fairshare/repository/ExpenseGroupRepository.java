package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, UUID> {
    @Query("select gm.group from GroupMember gm where gm.user.id = :userId and gm.active = true and gm.group.archived = false order by gm.group.updatedAt desc")
    List<ExpenseGroup> findActiveForUser(UUID userId);
    List<ExpenseGroup> findByOwnerIdAndArchivedFalse(UUID ownerId);
}
