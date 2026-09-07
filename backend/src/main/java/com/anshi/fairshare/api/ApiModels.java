package com.anshi.fairshare.api;

import com.anshi.fairshare.domain.DomainEnums.RecurrenceFrequency;
import com.anshi.fairshare.domain.DomainEnums.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record UserResponse(UUID id, String email, String displayName, String avatarUrl, String avatarPublicId) {}
    public record ProfileUpdateRequest(@NotBlank @Size(max = 100) String displayName, @Size(max = 1000) String avatarUrl, @Size(max = 255) String avatarPublicId) {}
    public record DeviceRequest(@NotBlank @Size(max = 512) String token, @NotBlank @Pattern(regexp = "ANDROID") String platform) {}

    public record GroupCreateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}
    public record GroupResponse(UUID id, String name, String description, UUID ownerId, boolean archived, long version, Instant createdAt) {}
    public record MemberResponse(UUID userId, String displayName, String email, String avatarUrl, String role, Instant joinedAt) {}
    public record InviteResponse(UUID id, UUID groupId, String token, String inviteUrl, Instant expiresAt, int maxUses, int useCount, boolean revoked) {}

    public record MoneyInput(@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency, @NotBlank String amount) {}
    public record PayerInput(@NotNull UUID userId, @NotBlank String amount) {}
    public record SplitInput(@NotNull UUID userId, String amount, String percentage) {}

    public record ExpenseRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @Valid MoneyInput total,
            @NotEmpty List<@Valid PayerInput> payers,
            @NotNull SplitType splitType,
            @NotEmpty List<@Valid SplitInput> splits,
            @NotNull @PastOrPresent LocalDate expenseDate
    ) {}

    public record PayerResponse(UUID userId, String displayName, String amount) {}
    public record ShareResponse(UUID userId, String displayName, String amount, String percentage) {}
    public record AllocationPreviewResponse(String currency, String total, List<PayerResponse> payers, SplitType splitType, List<ShareResponse> shares) {}
    public record ExpenseResponse(UUID id, UUID groupId, String description, String currency, String total, SplitType splitType,
                                  LocalDate expenseDate, List<PayerResponse> payers, List<ShareResponse> shares,
                                  UUID createdBy, long version, Instant createdAt, Instant updatedAt) {}

    public record SettlementRequest(@NotNull UUID fromUserId, @NotNull UUID toUserId, @NotNull @Valid MoneyInput money,
                                    @NotNull @PastOrPresent LocalDate settlementDate, @Size(max = 300) String note) {}
    public record SettlementResponse(UUID id, UUID groupId, UUID fromUserId, String fromName, UUID toUserId, String toName,
                                     String currency, String amount, LocalDate settlementDate, String note, UUID createdBy, long version, Instant createdAt) {}

    public record MemberBalance(UUID userId, String displayName, String netAmount) {}
    public record SettlementSuggestion(UUID fromUserId, UUID toUserId, String amount) {}
    public record CurrencyBalance(String currency, List<MemberBalance> members, List<SettlementSuggestion> suggestions) {}
    public record BalanceResponse(UUID groupId, List<CurrencyBalance> currencies) {}

    public record RecurringExpenseRequest(@NotNull @Valid ExpenseRequest expense, @NotNull RecurrenceFrequency frequency,
                                          @NotNull LocalDate firstRunDate, @NotBlank @Size(max = 60) String zoneId) {}
    public record RecurringExpenseResponse(UUID id, UUID groupId, String description, String currency, String total,
                                           SplitType splitType, RecurrenceFrequency frequency, LocalDate nextRunDate,
                                           String zoneId, boolean active, long version) {}

    public record ActivityResponse(UUID id, UUID actorId, String actorName, String eventType, String entityType,
                                   UUID entityId, String summary, Instant createdAt) {}
    public record NotificationResponse(UUID id, String type, String title, String body, Map<String, String> data,
                                       Instant readAt, Instant createdAt) {}
    public record PageResponse<T>(List<T> items, String nextCursor) {}
    public record MediaSignatureResponse(String cloudName, String apiKey, long timestamp, String signature, String folder, String publicId) {}
}
