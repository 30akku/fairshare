package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    Page<Expense> findByGroupIdAndDeletedFalse(UUID groupId, Pageable pageable);
    List<Expense> findByGroupIdAndDeletedFalse(UUID groupId);
    Optional<Expense> findByIdAndDeletedFalse(UUID id);
    Optional<Expense> findByRecurrenceOccurrenceKey(String key);
}

