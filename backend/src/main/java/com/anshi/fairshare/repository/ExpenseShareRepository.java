package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, UUID> {
    List<ExpenseShare> findByExpenseId(UUID expenseId);
    List<ExpenseShare> findByExpenseGroupIdAndExpenseDeletedFalse(UUID groupId);
    void deleteByExpenseId(UUID expenseId);
}

