package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.domain.*;
import com.anshi.fairshare.domain.DomainEnums.RecurrenceFrequency;
import com.anshi.fairshare.repository.RecurringExpenseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RecurringExpenseService {
    private final RecurringExpenseRepository recurringExpenses;
    private final ExpenseService expenseService;
    private final GroupAccessService access;
    private final UserService userService;
    private final ActivityService activity;
    private final ObjectMapper objectMapper;
    private final AtomicReference<LocalDate> lastCatchup = new AtomicReference<>();

    public RecurringExpenseService(RecurringExpenseRepository recurringExpenses, ExpenseService expenseService,
                                   GroupAccessService access, UserService userService, ActivityService activity, ObjectMapper objectMapper) {
        this.recurringExpenses = recurringExpenses; this.expenseService = expenseService; this.access = access;
        this.userService = userService; this.activity = activity; this.objectMapper = objectMapper;
    }

    @Transactional
    public RecurringExpenseResponse create(UUID groupId, UUID userId, RecurringExpenseRequest request) {
        ExpenseGroup group = access.requireMember(groupId, userId);
        AppUser actor = userService.require(userId);
        try { ZoneId.of(request.zoneId()); } catch (Exception exception) { throw ApiException.badRequest("INVALID_TIME_ZONE", "Use a valid IANA time zone"); }
        expenseService.preview(groupId, userId, request.expense());
        RecurringExpense recurring = new RecurringExpense();
        recurring.setGroup(group); recurring.setDescription(request.expense().description().trim());
        recurring.setCurrency(request.expense().total().currency().toUpperCase());
        recurring.setTotal(new java.math.BigDecimal(request.expense().total().amount())); recurring.setSplitType(request.expense().splitType());
        try {
            recurring.setPayerConfiguration(objectMapper.writeValueAsString(request.expense().payers()));
            recurring.setSplitConfiguration(objectMapper.writeValueAsString(request.expense().splits()));
        } catch (Exception exception) { throw new IllegalStateException("Unable to store recurrence configuration", exception); }
        recurring.setFrequency(request.frequency()); recurring.setNextRunDate(request.firstRunDate());
        recurring.setAnchorDay(request.firstRunDate().getDayOfMonth()); recurring.setZoneId(request.zoneId()); recurring.setCreatedBy(actor);
        recurringExpenses.save(recurring);
        activity.record(group, actor, "RECURRENCE_CREATED", "RECURRING_EXPENSE", recurring.getId(), actor.getDisplayName() + " scheduled " + recurring.getDescription());
        return response(recurring);
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> list(UUID groupId, UUID userId) {
        access.requireMember(groupId, userId);
        return recurringExpenses.findByGroupIdOrderByCreatedAtDesc(groupId).stream().map(this::response).toList();
    }

    @Transactional
    public RecurringExpenseResponse setActive(UUID groupId, UUID recurrenceId, UUID userId, boolean active, long expectedVersion) {
        ExpenseGroup group = access.requireMember(groupId, userId);
        RecurringExpense recurring = recurringExpenses.findById(recurrenceId).filter(value -> value.getGroup().getId().equals(groupId))
                .orElseThrow(() -> ApiException.notFound("Recurring expense"));
        if (!recurring.getCreatedBy().getId().equals(userId) && !group.getOwner().getId().equals(userId))
            throw ApiException.forbidden("Only the creator or group owner can change this schedule");
        if (recurring.getVersion() != expectedVersion) throw ApiException.conflict("STALE_VERSION", "The schedule changed; reload it and try again");
        recurring.setActive(active);
        return response(recurring);
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    @Transactional
    public void scheduledCatchup() { processDue(LocalDate.now(ZoneOffset.UTC)); }

    @Async
    @Transactional
    public void catchUpOncePerDay() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate previous = lastCatchup.get();
        if ((previous == null || previous.isBefore(today)) && lastCatchup.compareAndSet(previous, today)) processDue(today);
    }

    @Transactional
    public ProcessResult processDue(LocalDate throughDate) {
        int created = 0;
        List<RecurringExpense> due = recurringExpenses.findByActiveTrueAndNextRunDateLessThanEqualOrderByNextRunDate(throughDate);
        for (RecurringExpense recurring : due) {
            while (recurring.isActive() && !recurring.getNextRunDate().isAfter(throughDate)) {
                LocalDate occurrenceDate = recurring.getNextRunDate();
                try {
                    List<PayerInput> payers = objectMapper.readValue(recurring.getPayerConfiguration(), new TypeReference<>() {});
                    List<SplitInput> splits = objectMapper.readValue(recurring.getSplitConfiguration(), new TypeReference<>() {});
                    ExpenseRequest request = new ExpenseRequest(recurring.getDescription(),
                            new MoneyInput(recurring.getCurrency(), recurring.getTotal().toPlainString()), payers,
                            recurring.getSplitType(), splits, occurrenceDate);
                    String key = "recurrence:" + recurring.getId() + ":" + occurrenceDate;
                    expenseService.create(recurring.getGroup().getId(), recurring.getCreatedBy().getId(), key, request, key);
                    recurring.setNextRunDate(nextDate(recurring, occurrenceDate));
                    created++;
                } catch (ApiException exception) {
                    if (exception.status().is4xxClientError()) recurring.setActive(false);
                    break;
                } catch (Exception exception) {
                    break;
                }
            }
        }
        return new ProcessResult(due.size(), created);
    }

    private LocalDate nextDate(RecurringExpense recurring, LocalDate current) {
        if (recurring.getFrequency() == RecurrenceFrequency.WEEKLY) return current.plusWeeks(1);
        YearMonth nextMonth = YearMonth.from(current).plusMonths(1);
        return nextMonth.atDay(Math.min(recurring.getAnchorDay(), nextMonth.lengthOfMonth()));
    }

    private RecurringExpenseResponse response(RecurringExpense recurring) {
        return new RecurringExpenseResponse(recurring.getId(), recurring.getGroup().getId(), recurring.getDescription(),
                recurring.getCurrency(), recurring.getTotal().stripTrailingZeros().toPlainString(), recurring.getSplitType(),
                recurring.getFrequency(), recurring.getNextRunDate(), recurring.getZoneId(), recurring.isActive(), recurring.getVersion());
    }

    public record ProcessResult(int templatesDue, int expensesCreated) {}
}
