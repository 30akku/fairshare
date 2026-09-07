package com.anshi.fairshare.api;

import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.security.AuthenticatedUser;
import com.anshi.fairshare.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class LedgerController {
    private final BalanceService balances;
    private final SettlementService settlements;
    private final ActivityService activity;
    public LedgerController(BalanceService balances, SettlementService settlements, ActivityService activity) {
        this.balances = balances; this.settlements = settlements; this.activity = activity;
    }

    @GetMapping("/balances")
    BalanceResponse balances(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return balances.calculate(groupId, principal.id());
    }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    SettlementResponse settle(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal,
                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                              @Valid @RequestBody SettlementRequest request) {
        return settlements.create(groupId, principal.id(), idempotencyKey, request);
    }

    @GetMapping("/settlements")
    PageResponse<SettlementResponse> settlements(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return settlements.list(groupId, principal.id(), Math.max(page, 0), Math.max(size, 1));
    }

    @DeleteMapping("/settlements/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSettlement(@PathVariable UUID groupId, @PathVariable UUID settlementId,
                          @AuthenticationPrincipal AuthenticatedUser principal, @RequestHeader("If-Match") long expectedVersion) {
        settlements.delete(groupId, settlementId, principal.id(), expectedVersion);
    }

    @GetMapping("/activity")
    PageResponse<ActivityResponse> activity(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal,
                                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return activity.list(groupId, principal.id(), Math.max(page, 0), Math.max(size, 1));
    }
}

