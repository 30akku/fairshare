package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.domain.*;
import com.anshi.fairshare.repository.*;
import com.anshi.fairshare.service.AllocationService.CalculatedAllocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ExpenseService {
    private final ExpenseRepository expenses;
    private final ExpensePayerRepository payers;
    private final ExpenseShareRepository shares;
    private final IdempotencyRecordRepository idempotency;
    private final AllocationService allocationService;
    private final GroupAccessService access;
    private final UserService userService;
    private final GroupMemberRepository members;
    private final ActivityService activity;
    private final NotificationService notificationService;

    public ExpenseService(ExpenseRepository expenses, ExpensePayerRepository payers, ExpenseShareRepository shares,
                          IdempotencyRecordRepository idempotency, AllocationService allocationService,
                          GroupAccessService access, UserService userService, GroupMemberRepository members,
                          ActivityService activity, NotificationService notificationService) {
        this.expenses = expenses; this.payers = payers; this.shares = shares; this.idempotency = idempotency;
        this.allocationService = allocationService; this.access = access; this.userService = userService;
        this.members = members; this.activity = activity; this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public AllocationPreviewResponse preview(UUID groupId, UUID userId, ExpenseRequest request) {
        access.requireMember(groupId, userId);
        CalculatedAllocation allocation = allocationService.calculate(request);
        Map<UUID, AppUser> users = requireAllocationMembers(groupId, allocation);
        return previewResponse(allocation, users);
    }

    @Transactional
    public ExpenseResponse create(UUID groupId, UUID userId, String idempotencyKey, ExpenseRequest request) {
        return create(groupId, userId, idempotencyKey, request, null);
    }

    @Transactional
    public ExpenseResponse create(UUID groupId, UUID userId, String idempotencyKey, ExpenseRequest request, String recurrenceKey) {
        validateIdempotencyKey(idempotencyKey);
        ExpenseGroup group = access.requireMember(groupId, userId);
        AppUser actor = userService.require(userId);
        String operation = "EXPENSE:" + groupId;
        Optional<IdempotencyRecord> existing = idempotency.findByUserIdAndIdempotencyKeyAndOperation(userId, idempotencyKey, operation);
        if (existing.isPresent()) return response(expenses.findById(existing.get().getResourceId()).orElseThrow(() -> ApiException.notFound("Expense")));
        if (recurrenceKey != null) {
            Optional<Expense> occurrence = expenses.findByRecurrenceOccurrenceKey(recurrenceKey);
            if (occurrence.isPresent()) return response(occurrence.get());
        }

        CalculatedAllocation allocation = allocationService.calculate(request);
        Map<UUID, AppUser> allocationUsers = requireAllocationMembers(groupId, allocation);
        Expense expense = new Expense();
        apply(expense, group, actor, request, allocation);
        expense.setRecurrenceOccurrenceKey(recurrenceKey);
        expenses.save(expense);
        saveAllocations(expense, allocation, allocationUsers);
        saveIdempotency(actor, idempotencyKey, operation, expense.getId());
        activity.record(group, actor, "EXPENSE_CREATED", "EXPENSE", expense.getId(), actor.getDisplayName() + " added " + expense.getDescription());
        notifyGroup(groupId, userId, "EXPENSE_CREATED", "New expense", actor.getDisplayName() + " added " + expense.getDescription(), expense.getId());
        return response(expense);
    }

    @Transactional
    public ExpenseResponse update(UUID groupId, UUID expenseId, UUID userId, long expectedVersion, ExpenseRequest request) {
        ExpenseGroup group = access.requireMember(groupId, userId);
        Expense expense = requireExpense(groupId, expenseId);
        if (!expense.getCreatedBy().getId().equals(userId) && !group.getOwner().getId().equals(userId))
            throw ApiException.forbidden("Only the expense creator or group owner can edit this expense");
        if (expense.getVersion() != expectedVersion) throw ApiException.conflict("STALE_VERSION", "The expense changed; reload it and try again");
        CalculatedAllocation allocation = allocationService.calculate(request);
        Map<UUID, AppUser> allocationUsers = requireAllocationMembers(groupId, allocation);
        payers.deleteByExpenseId(expenseId); shares.deleteByExpenseId(expenseId);
        apply(expense, group, expense.getCreatedBy(), request, allocation);
        saveAllocations(expense, allocation, allocationUsers);
        AppUser actor = userService.require(userId);
        activity.record(group, actor, "EXPENSE_UPDATED", "EXPENSE", expenseId, actor.getDisplayName() + " updated " + expense.getDescription());
        notifyGroup(groupId, userId, "EXPENSE_UPDATED", "Expense updated", expense.getDescription() + " was updated", expenseId);
        return response(expense);
    }

    @Transactional
    public void delete(UUID groupId, UUID expenseId, UUID userId, long expectedVersion) {
        ExpenseGroup group = access.requireMember(groupId, userId);
        Expense expense = requireExpense(groupId, expenseId);
        if (!expense.getCreatedBy().getId().equals(userId) && !group.getOwner().getId().equals(userId))
            throw ApiException.forbidden("Only the expense creator or group owner can delete this expense");
        if (expense.getVersion() != expectedVersion) throw ApiException.conflict("STALE_VERSION", "The expense changed; reload it and try again");
        expense.setDeleted(true);
        AppUser actor = userService.require(userId);
        activity.record(group, actor, "EXPENSE_DELETED", "EXPENSE", expenseId, actor.getDisplayName() + " deleted " + expense.getDescription());
        notifyGroup(groupId, userId, "EXPENSE_DELETED", "Expense deleted", expense.getDescription() + " was deleted", expenseId);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID groupId, UUID expenseId, UUID userId) {
        access.requireMember(groupId, userId);
        return response(requireExpense(groupId, expenseId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(UUID groupId, UUID userId, int page, int size) {
        access.requireMember(groupId, userId);
        Page<Expense> result = expenses.findByGroupIdAndDeletedFalse(groupId,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"))));
        return new PageResponse<>(result.map(this::response).getContent(), result.hasNext() ? String.valueOf(page + 1) : null);
    }

    private void apply(Expense expense, ExpenseGroup group, AppUser actor, ExpenseRequest request, CalculatedAllocation allocation) {
        expense.setGroup(group); expense.setDescription(request.description().trim()); expense.setCurrency(allocation.currency());
        expense.setTotal(allocation.total()); expense.setSplitType(allocation.splitType()); expense.setExpenseDate(request.expenseDate());
        expense.setCreatedBy(actor);
    }

    private Map<UUID, AppUser> requireAllocationMembers(UUID groupId, CalculatedAllocation allocation) {
        Set<UUID> ids = new HashSet<>(allocation.payers().keySet());
        allocation.shares().forEach(share -> ids.add(share.userId()));
        return access.requireActiveUsers(groupId, ids);
    }

    private void saveAllocations(Expense expense, CalculatedAllocation allocation, Map<UUID, AppUser> users) {
        List<ExpensePayer> payerEntities = allocation.payers().entrySet().stream().map(entry -> {
            ExpensePayer payer = new ExpensePayer(); payer.setExpense(expense); payer.setUser(users.get(entry.getKey())); payer.setAmount(entry.getValue()); return payer;
        }).toList();
        List<ExpenseShare> shareEntities = allocation.shares().stream().map(value -> {
            ExpenseShare share = new ExpenseShare(); share.setExpense(expense); share.setUser(users.get(value.userId()));
            share.setAmount(value.amount()); share.setPercentage(value.percentage()); return share;
        }).toList();
        payers.saveAll(payerEntities); shares.saveAll(shareEntities);
    }

    private void saveIdempotency(AppUser user, String key, String operation, UUID resourceId) {
        IdempotencyRecord record = new IdempotencyRecord(); record.setUser(user); record.setIdempotencyKey(key);
        record.setOperation(operation); record.setResourceId(resourceId); idempotency.save(record);
    }

    private Expense requireExpense(UUID groupId, UUID expenseId) {
        return expenses.findByIdAndDeletedFalse(expenseId).filter(expense -> expense.getGroup().getId().equals(groupId))
                .orElseThrow(() -> ApiException.notFound("Expense"));
    }

    private ExpenseResponse response(Expense expense) {
        List<PayerResponse> payerResponses = payers.findByExpenseId(expense.getId()).stream()
                .sorted(Comparator.comparing(value -> value.getUser().getId().toString()))
                .map(value -> new PayerResponse(value.getUser().getId(), value.getUser().getDisplayName(), allocationService.format(value.getAmount()))).toList();
        List<ShareResponse> shareResponses = shares.findByExpenseId(expense.getId()).stream()
                .sorted(Comparator.comparing(value -> value.getUser().getId().toString()))
                .map(value -> new ShareResponse(value.getUser().getId(), value.getUser().getDisplayName(), allocationService.format(value.getAmount()),
                        value.getPercentage() == null ? null : allocationService.format(value.getPercentage()))).toList();
        return new ExpenseResponse(expense.getId(), expense.getGroup().getId(), expense.getDescription(), expense.getCurrency(),
                allocationService.format(expense.getTotal()), expense.getSplitType(), expense.getExpenseDate(), payerResponses, shareResponses,
                expense.getCreatedBy().getId(), expense.getVersion(), expense.getCreatedAt(), expense.getUpdatedAt());
    }

    private AllocationPreviewResponse previewResponse(CalculatedAllocation allocation, Map<UUID, AppUser> users) {
        List<PayerResponse> payerResponses = allocation.payers().entrySet().stream().map(entry ->
                new PayerResponse(entry.getKey(), users.get(entry.getKey()).getDisplayName(), allocationService.format(entry.getValue()))).toList();
        List<ShareResponse> shareResponses = allocation.shares().stream().map(share -> new ShareResponse(share.userId(),
                users.get(share.userId()).getDisplayName(), allocationService.format(share.amount()),
                share.percentage() == null ? null : allocationService.format(share.percentage()))).toList();
        return new AllocationPreviewResponse(allocation.currency(), allocationService.format(allocation.total()), payerResponses, allocation.splitType(), shareResponses);
    }

    private void notifyGroup(UUID groupId, UUID actorId, String type, String title, String body, UUID expenseId) {
        List<AppUser> recipients = members.findByGroupIdAndActiveTrueOrderByJoinedAt(groupId).stream().map(GroupMember::getUser)
                .filter(user -> !user.getId().equals(actorId)).toList();
        notificationService.notifyUsers(recipients, type, title, body, Map.of("groupId", groupId.toString(), "expenseId", expenseId.toString()));
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 150)
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 150 characters");
    }
}
