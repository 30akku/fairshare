package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.domain.*;
import com.anshi.fairshare.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class BalanceService {
    private final ExpensePayerRepository payers;
    private final ExpenseShareRepository shares;
    private final SettlementRepository settlements;
    private final GroupAccessService access;
    private final AllocationService allocationService;

    public BalanceService(ExpensePayerRepository payers, ExpenseShareRepository shares, SettlementRepository settlements,
                          GroupAccessService access, AllocationService allocationService) {
        this.payers = payers; this.shares = shares; this.settlements = settlements; this.access = access; this.allocationService = allocationService;
    }

    @Transactional(readOnly = true)
    public BalanceResponse calculate(UUID groupId, UUID userId) {
        access.requireMember(groupId, userId);
        Map<String, Map<UUID, BigDecimal>> nets = new TreeMap<>();
        Map<UUID, AppUser> users = new HashMap<>();
        for (ExpensePayer payer : payers.findByExpenseGroupIdAndExpenseDeletedFalse(groupId)) {
            users.put(payer.getUser().getId(), payer.getUser());
            add(nets, payer.getExpense().getCurrency(), payer.getUser().getId(), payer.getAmount());
        }
        for (ExpenseShare share : shares.findByExpenseGroupIdAndExpenseDeletedFalse(groupId)) {
            users.put(share.getUser().getId(), share.getUser());
            add(nets, share.getExpense().getCurrency(), share.getUser().getId(), share.getAmount().negate());
        }
        for (Settlement settlement : settlements.findByGroupIdAndDeletedFalse(groupId)) {
            users.put(settlement.getFromUser().getId(), settlement.getFromUser()); users.put(settlement.getToUser().getId(), settlement.getToUser());
            add(nets, settlement.getCurrency(), settlement.getFromUser().getId(), settlement.getAmount());
            add(nets, settlement.getCurrency(), settlement.getToUser().getId(), settlement.getAmount().negate());
        }
        List<CurrencyBalance> currencies = nets.entrySet().stream().map(entry -> currencyBalance(entry.getKey(), entry.getValue(), users)).toList();
        return new BalanceResponse(groupId, currencies);
    }

    private CurrencyBalance currencyBalance(String currency, Map<UUID, BigDecimal> net, Map<UUID, AppUser> users) {
        List<MemberBalance> members = net.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .map(entry -> new MemberBalance(entry.getKey(), users.get(entry.getKey()).getDisplayName(), allocationService.format(entry.getValue())))
                .toList();
        return new CurrencyBalance(currency, members, suggestions(net));
    }

    private List<SettlementSuggestion> suggestions(Map<UUID, BigDecimal> source) {
        List<Position> debtors = new ArrayList<>();
        List<Position> creditors = new ArrayList<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString))).forEach(entry -> {
            if (entry.getValue().signum() < 0) debtors.add(new Position(entry.getKey(), entry.getValue().negate()));
            else if (entry.getValue().signum() > 0) creditors.add(new Position(entry.getKey(), entry.getValue()));
        });
        List<SettlementSuggestion> result = new ArrayList<>();
        int debtorIndex = 0, creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            Position debtor = debtors.get(debtorIndex); Position creditor = creditors.get(creditorIndex);
            BigDecimal amount = debtor.amount.min(creditor.amount);
            if (amount.signum() > 0) result.add(new SettlementSuggestion(debtor.userId, creditor.userId, allocationService.format(amount)));
            debtor.amount = debtor.amount.subtract(amount); creditor.amount = creditor.amount.subtract(amount);
            if (debtor.amount.signum() == 0) debtorIndex++;
            if (creditor.amount.signum() == 0) creditorIndex++;
        }
        return result;
    }

    private void add(Map<String, Map<UUID, BigDecimal>> nets, String currency, UUID userId, BigDecimal amount) {
        nets.computeIfAbsent(currency, ignored -> new HashMap<>()).merge(userId, amount, BigDecimal::add);
    }

    private static final class Position {
        private final UUID userId; private BigDecimal amount;
        private Position(UUID userId, BigDecimal amount) { this.userId = userId; this.amount = amount; }
    }
}

