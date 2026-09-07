package com.anshi.fairshare.api;

import com.anshi.fairshare.api.ApiModels.RecurringExpenseRequest;
import com.anshi.fairshare.api.ApiModels.RecurringExpenseResponse;
import com.anshi.fairshare.security.AuthenticatedUser;
import com.anshi.fairshare.service.RecurringExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/recurring-expenses")
public class RecurringExpenseController {
    private final RecurringExpenseService recurring;
    public RecurringExpenseController(RecurringExpenseService recurring) { this.recurring = recurring; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RecurringExpenseResponse create(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal,
                                    @Valid @RequestBody RecurringExpenseRequest request) {
        return recurring.create(groupId, principal.id(), request);
    }

    @GetMapping
    List<RecurringExpenseResponse> list(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return recurring.list(groupId, principal.id());
    }

    @PatchMapping("/{recurrenceId}")
    RecurringExpenseResponse setActive(@PathVariable UUID groupId, @PathVariable UUID recurrenceId,
                                       @AuthenticationPrincipal AuthenticatedUser principal,
                                       @RequestHeader("If-Match") long expectedVersion,
                                       @RequestParam boolean active) {
        return recurring.setActive(groupId, recurrenceId, principal.id(), active, expectedVersion);
    }
}

