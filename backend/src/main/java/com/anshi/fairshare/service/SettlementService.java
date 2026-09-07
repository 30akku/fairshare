package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.domain.*;
import com.anshi.fairshare.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SettlementService {
    private final SettlementRepository settlements;
    private final IdempotencyRecordRepository idempotency;
    private final GroupAccessService access;
    private final UserService userService;
    private final AllocationService allocationService;
    private final GroupMemberRepository members;
    private final ActivityService activity;
    private final NotificationService notificationService;

    public SettlementService(SettlementRepository settlements, IdempotencyRecordRepository idempotency,
                             GroupAccessService access, UserService userService, AllocationService allocationService,
                             GroupMemberRepository members, ActivityService activity, NotificationService notificationService) {
        this.settlements = settlements; this.idempotency = idempotency; this.access = access; this.userService = userService;
        this.allocationService = allocationService; this.members = members; this.activity = activity; this.notificationService = notificationService;
    }

    @Transactional
    public SettlementResponse create(UUID groupId, UUID userId, String idempotencyKey, SettlementRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 150)
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 150 characters");
        ExpenseGroup group = access.requireMember(groupId, userId);
        if (request.fromUserId().equals(request.toUserId())) throw ApiException.badRequest("SAME_SETTLEMENT_USER", "Payer and recipient must be different members");
        if (!userId.equals(request.fromUserId()) && !userId.equals(request.toUserId()) && !group.getOwner().getId().equals(userId))
            throw ApiException.forbidden("Only an involved member or the group owner can record this settlement");
        Map<UUID, AppUser> settlementUsers = access.requireActiveUsers(groupId, List.of(request.fromUserId(), request.toUserId()));
        String operation = "SETTLE:" + groupId;
        Optional<IdempotencyRecord> existing = idempotency.findByUserIdAndIdempotencyKeyAndOperation(userId, idempotencyKey, operation);
        if (existing.isPresent()) return response(settlements.findById(existing.get().getResourceId()).orElseThrow(() -> ApiException.notFound("Settlement")));
        String currency = request.money().currency().toUpperCase(Locale.ROOT);
        BigDecimal amount = allocationService.money(request.money().amount(), currency, true, "settlement amount");
        AppUser actor = userService.require(userId);
        Settlement settlement = new Settlement();
        settlement.setGroup(group); settlement.setFromUser(settlementUsers.get(request.fromUserId())); settlement.setToUser(settlementUsers.get(request.toUserId()));
        settlement.setCurrency(currency); settlement.setAmount(amount); settlement.setSettlementDate(request.settlementDate());
        settlement.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim()); settlement.setCreatedBy(actor);
        settlements.save(settlement);
        IdempotencyRecord record = new IdempotencyRecord(); record.setUser(actor); record.setIdempotencyKey(idempotencyKey);
        record.setOperation(operation); record.setResourceId(settlement.getId()); idempotency.save(record);
        activity.record(group, actor, "SETTLEMENT_CREATED", "SETTLEMENT", settlement.getId(),
                settlement.getFromUser().getDisplayName() + " paid " + settlement.getToUser().getDisplayName());
        List<AppUser> recipients = members.findByGroupIdAndActiveTrueOrderByJoinedAt(groupId).stream().map(GroupMember::getUser)
                .filter(user -> !user.getId().equals(userId)).toList();
        notificationService.notifyUsers(recipients, "SETTLEMENT_CREATED", "Payment recorded",
                settlement.getFromUser().getDisplayName() + " paid " + settlement.getToUser().getDisplayName(),
                Map.of("groupId", groupId.toString(), "settlementId", settlement.getId().toString()));
        return response(settlement);
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementResponse> list(UUID groupId, UUID userId, int page, int size) {
        access.requireMember(groupId, userId);
        Page<Settlement> result = settlements.findByGroupIdAndDeletedFalse(groupId,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Order.desc("settlementDate"), Sort.Order.desc("createdAt"))));
        return new PageResponse<>(result.map(this::response).getContent(), result.hasNext() ? String.valueOf(page + 1) : null);
    }

    @Transactional
    public void delete(UUID groupId, UUID settlementId, UUID userId, long expectedVersion) {
        ExpenseGroup group = access.requireMember(groupId, userId);
        Settlement settlement = settlements.findByIdAndDeletedFalse(settlementId).filter(value -> value.getGroup().getId().equals(groupId))
                .orElseThrow(() -> ApiException.notFound("Settlement"));
        if (!settlement.getCreatedBy().getId().equals(userId) && !group.getOwner().getId().equals(userId))
            throw ApiException.forbidden("Only the settlement creator or group owner can delete it");
        if (settlement.getVersion() != expectedVersion) throw ApiException.conflict("STALE_VERSION", "The settlement changed; reload it and try again");
        settlement.setDeleted(true);
        activity.record(group, userService.require(userId), "SETTLEMENT_DELETED", "SETTLEMENT", settlementId, "A settlement was deleted");
    }

    private SettlementResponse response(Settlement settlement) {
        return new SettlementResponse(settlement.getId(), settlement.getGroup().getId(), settlement.getFromUser().getId(),
                settlement.getFromUser().getDisplayName(), settlement.getToUser().getId(), settlement.getToUser().getDisplayName(),
                settlement.getCurrency(), allocationService.format(settlement.getAmount()), settlement.getSettlementDate(), settlement.getNote(),
                settlement.getCreatedBy().getId(), settlement.getVersion(), settlement.getCreatedAt());
    }
}
