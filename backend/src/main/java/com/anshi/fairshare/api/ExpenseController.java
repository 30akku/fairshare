package com.anshi.fairshare.api;

import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.security.AuthenticatedUser;
import com.anshi.fairshare.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/expenses")
public class ExpenseController {
    private final ExpenseService expenses;
    public ExpenseController(ExpenseService expenses) { this.expenses = expenses; }

    @PostMapping("/preview")
    AllocationPreviewResponse preview(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal,
                                      @Valid @RequestBody ExpenseRequest request) {
        return expenses.preview(groupId, principal.id(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExpenseResponse create(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal,
                           @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody ExpenseRequest request) {
        return expenses.create(groupId, principal.id(), idempotencyKey, request);
    }

    @GetMapping
    PageResponse<ExpenseResponse> list(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return expenses.list(groupId, principal.id(), Math.max(0, page), Math.max(1, size));
    }

    @GetMapping("/{expenseId}")
    ExpenseResponse get(@PathVariable UUID groupId, @PathVariable UUID expenseId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return expenses.get(groupId, expenseId, principal.id());
    }

    @PutMapping("/{expenseId}")
    ExpenseResponse update(@PathVariable UUID groupId, @PathVariable UUID expenseId,
                           @AuthenticationPrincipal AuthenticatedUser principal, @RequestHeader("If-Match") long expectedVersion,
                           @Valid @RequestBody ExpenseRequest request) {
        return expenses.update(groupId, expenseId, principal.id(), expectedVersion, request);
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID groupId, @PathVariable UUID expenseId, @AuthenticationPrincipal AuthenticatedUser principal,
                @RequestHeader("If-Match") long expectedVersion) {
        expenses.delete(groupId, expenseId, principal.id(), expectedVersion);
    }
}

