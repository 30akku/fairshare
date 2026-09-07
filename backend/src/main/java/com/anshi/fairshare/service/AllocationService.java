package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.ExpenseRequest;
import com.anshi.fairshare.api.ApiModels.PayerInput;
import com.anshi.fairshare.api.ApiModels.SplitInput;
import com.anshi.fairshare.domain.DomainEnums.SplitType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class AllocationService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0000");

    public CalculatedAllocation calculate(ExpenseRequest request) {
        String currency = normalizeCurrency(request.total().currency());
        int scale = currencyScale(currency);
        BigDecimal total = money(request.total().amount(), currency, true, "total");
        LinkedHashMap<UUID, BigDecimal> payers = payerAmounts(request.payers(), currency, total);
        List<CalculatedShare> shares = switch (request.splitType()) {
            case EQUAL -> equalShares(request.splits(), total, scale);
            case EXACT -> exactShares(request.splits(), total, currency);
            case PERCENTAGE -> percentageShares(request.splits(), total, scale);
        };
        return new CalculatedAllocation(currency, total, payers, request.splitType(), shares);
    }

    public BigDecimal money(String value, String currency, boolean positive, String field) {
        BigDecimal parsed = decimal(value, field);
        int scale = currencyScale(normalizeCurrency(currency));
        try { parsed = parsed.setScale(scale, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) {
            throw ApiException.badRequest("INVALID_MONEY_SCALE", field + " supports at most " + scale + " decimal places for " + currency);
        }
        if (positive && parsed.signum() <= 0) throw ApiException.badRequest("INVALID_AMOUNT", field + " must be greater than zero");
        if (!positive && parsed.signum() < 0) throw ApiException.badRequest("INVALID_AMOUNT", field + " cannot be negative");
        if (parsed.precision() > 19) throw ApiException.badRequest("AMOUNT_TOO_LARGE", field + " is too large");
        return parsed;
    }

    public String format(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private LinkedHashMap<UUID, BigDecimal> payerAmounts(List<PayerInput> inputs, String currency, BigDecimal total) {
        LinkedHashMap<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (PayerInput input : inputs) {
            if (result.containsKey(input.userId())) throw ApiException.badRequest("DUPLICATE_PAYER", "Each payer may appear only once");
            result.put(input.userId(), money(input.amount(), currency, true, "payer amount"));
        }
        BigDecimal sum = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) throw ApiException.badRequest("PAYER_TOTAL_MISMATCH", "Payer contributions must equal the expense total");
        return result;
    }

    private List<CalculatedShare> equalShares(List<SplitInput> inputs, BigDecimal total, int scale) {
        List<SplitInput> sorted = uniqueAndSorted(inputs);
        BigDecimal count = BigDecimal.valueOf(sorted.size());
        BigDecimal base = total.divide(count, scale, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(count));
        BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
        List<CalculatedShare> result = new ArrayList<>();
        for (SplitInput input : sorted) {
            BigDecimal amount = base;
            if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                amount = amount.add(unit);
                remainder = remainder.subtract(unit);
            }
            result.add(new CalculatedShare(input.userId(), amount, null));
        }
        return result;
    }

    private List<CalculatedShare> exactShares(List<SplitInput> inputs, BigDecimal total, String currency) {
        List<SplitInput> sorted = uniqueAndSorted(inputs);
        List<CalculatedShare> result = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (SplitInput input : sorted) {
            if (input.amount() == null || input.amount().isBlank()) throw ApiException.badRequest("EXACT_AMOUNT_REQUIRED", "Every exact split needs an amount");
            BigDecimal amount = money(input.amount(), currency, false, "split amount");
            sum = sum.add(amount);
            result.add(new CalculatedShare(input.userId(), amount, null));
        }
        if (sum.compareTo(total) != 0) throw ApiException.badRequest("SPLIT_TOTAL_MISMATCH", "Exact shares must equal the expense total");
        return result;
    }

    private List<CalculatedShare> percentageShares(List<SplitInput> inputs, BigDecimal total, int scale) {
        List<SplitInput> sorted = uniqueAndSorted(inputs);
        List<BigDecimal> percentages = new ArrayList<>();
        BigDecimal percentageTotal = BigDecimal.ZERO;
        for (SplitInput input : sorted) {
            if (input.percentage() == null || input.percentage().isBlank()) throw ApiException.badRequest("PERCENTAGE_REQUIRED", "Every percentage split needs a percentage");
            BigDecimal percentage = decimal(input.percentage(), "percentage");
            if (percentage.scale() > 4) throw ApiException.badRequest("INVALID_PERCENTAGE_SCALE", "Percentages support at most four decimal places");
            percentage = percentage.setScale(4);
            if (percentage.signum() < 0 || percentage.compareTo(ONE_HUNDRED) > 0) throw ApiException.badRequest("INVALID_PERCENTAGE", "Percentages must be between 0 and 100");
            percentages.add(percentage);
            percentageTotal = percentageTotal.add(percentage);
        }
        if (percentageTotal.compareTo(ONE_HUNDRED) != 0) throw ApiException.badRequest("PERCENTAGE_TOTAL_MISMATCH", "Percentages must total exactly 100");

        List<CalculatedShare> result = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < sorted.size(); i++) {
            BigDecimal amount = total.multiply(percentages.get(i)).divide(ONE_HUNDRED, scale, RoundingMode.DOWN);
            allocated = allocated.add(amount);
            result.add(new CalculatedShare(sorted.get(i).userId(), amount, percentages.get(i)));
        }
        BigDecimal remainder = total.subtract(allocated);
        BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
        for (int i = 0; remainder.compareTo(BigDecimal.ZERO) > 0; i = (i + 1) % result.size()) {
            CalculatedShare current = result.get(i);
            result.set(i, new CalculatedShare(current.userId(), current.amount().add(unit), current.percentage()));
            remainder = remainder.subtract(unit);
        }
        return result;
    }

    private List<SplitInput> uniqueAndSorted(List<SplitInput> inputs) {
        if (inputs == null || inputs.isEmpty()) throw ApiException.badRequest("SPLITS_REQUIRED", "At least one participant is required");
        Set<UUID> ids = new HashSet<>();
        for (SplitInput input : inputs) if (!ids.add(input.userId())) throw ApiException.badRequest("DUPLICATE_PARTICIPANT", "Each participant may appear only once");
        return inputs.stream().sorted(Comparator.comparing(input -> input.userId().toString())).toList();
    }

    private BigDecimal decimal(String value, String field) {
        try {
            if (value == null || value.isBlank()) throw new NumberFormatException();
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw ApiException.badRequest("INVALID_DECIMAL", field + " must be a decimal string");
        }
    }

    private String normalizeCurrency(String currency) {
        try {
            String normalized = currency.toUpperCase(Locale.ROOT);
            Currency.getInstance(normalized);
            return normalized;
        } catch (Exception exception) {
            throw ApiException.badRequest("INVALID_CURRENCY", "Use a valid ISO-4217 currency code");
        }
    }

    private int currencyScale(String currency) {
        int scale = Currency.getInstance(currency).getDefaultFractionDigits();
        return Math.max(0, Math.min(scale, 4));
    }

    public record CalculatedAllocation(String currency, BigDecimal total, LinkedHashMap<UUID, BigDecimal> payers,
                                       SplitType splitType, List<CalculatedShare> shares) {}
    public record CalculatedShare(UUID userId, BigDecimal amount, BigDecimal percentage) {}
}

