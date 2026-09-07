package com.anshi.fairshare.repository;

import com.anshi.fairshare.domain.ExpensePayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpensePayerRepository extends JpaRepository<ExpensePayer, UUID> {
    List<ExpensePayer> findByExpenseId(UUID expenseId);
    List<ExpensePayer> findByExpenseGroupIdAndExpenseDeletedFalse(UUID groupId);
    void deleteByExpenseId(UUID expenseId);
}

