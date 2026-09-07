package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {
    List<RecurringExpense> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
    List<RecurringExpense> findByActiveTrueAndNextRunDateLessThanEqualOrderByNextRunDate(LocalDate date);
}

