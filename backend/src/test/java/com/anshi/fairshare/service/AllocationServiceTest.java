package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.domain.DomainEnums.SplitType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AllocationServiceTest {
    private final AllocationService service = new AllocationService();
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void acceptsMultiplePayersAndEqualSplitWithDeterministicRemainder() {
        var result = service.calculate(request("10.00", SplitType.EQUAL,
                List.of(new PayerInput(a, "6.00"), new PayerInput(b, "4.00")),
                List.of(new SplitInput(c, null, null), new SplitInput(b, null, null), new SplitInput(a, null, null))));

        assertThat(result.payers()).containsEntry(a, new java.math.BigDecimal("6.00")).containsEntry(b, new java.math.BigDecimal("4.00"));
        assertThat(result.shares()).extracting(value -> value.userId() + ":" + value.amount())
                .containsExactly(a + ":3.34", b + ":3.33", c + ":3.33");
    }

    @Test
    void calculatesPercentageSplitAndAssignsRemainderInMemberOrder() {
        var result = service.calculate(request("10.00", SplitType.PERCENTAGE,
                List.of(new PayerInput(a, "10.00")),
                List.of(new SplitInput(a, null, "33.3333"), new SplitInput(b, null, "33.3333"), new SplitInput(c, null, "33.3334"))));

        assertThat(result.shares()).extracting(value -> value.amount().toPlainString())
                .containsExactly("3.34", "3.33", "3.33");
    }

    @Test
    void acceptsExactSplitWherePayerIsNotParticipant() {
        var result = service.calculate(request("100.00", SplitType.EXACT,
                List.of(new PayerInput(a, "40.00"), new PayerInput(b, "60.00")),
                List.of(new SplitInput(c, "100.00", null))));
        assertThat(result.shares()).singleElement().extracting(value -> value.userId()).isEqualTo(c);
    }

    @Test
    void rejectsPayerTotalMismatch() {
        assertThatThrownBy(() -> service.calculate(request("10.00", SplitType.EQUAL,
                List.of(new PayerInput(a, "9.99")), List.of(new SplitInput(a, null, null)))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PAYER_TOTAL_MISMATCH");
    }

    @Test
    void rejectsDuplicatePayersAndParticipants() {
        assertThatThrownBy(() -> service.calculate(request("10.00", SplitType.EQUAL,
                List.of(new PayerInput(a, "5.00"), new PayerInput(a, "5.00")), List.of(new SplitInput(a, null, null)))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("DUPLICATE_PAYER");
        assertThatThrownBy(() -> service.calculate(request("10.00", SplitType.EQUAL,
                List.of(new PayerInput(a, "10.00")), List.of(new SplitInput(a, null, null), new SplitInput(a, null, null)))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("DUPLICATE_PARTICIPANT");
    }

    @Test
    void rejectsExactAndPercentageTotalsThatDoNotMatch() {
        assertThatThrownBy(() -> service.calculate(request("10.00", SplitType.EXACT,
                List.of(new PayerInput(a, "10.00")), List.of(new SplitInput(a, "9.99", null)))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SPLIT_TOTAL_MISMATCH");
        assertThatThrownBy(() -> service.calculate(request("10.00", SplitType.PERCENTAGE,
                List.of(new PayerInput(a, "10.00")), List.of(new SplitInput(a, null, "99.9999")))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PERCENTAGE_TOTAL_MISMATCH");
    }

    @Test
    void enforcesCurrencyPrecision() {
        assertThatThrownBy(() -> service.calculate(new ExpenseRequest("Train", new MoneyInput("JPY", "10.50"),
                List.of(new PayerInput(a, "10.50")), SplitType.EQUAL, List.of(new SplitInput(a, null, null)), LocalDate.now())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_MONEY_SCALE");
    }

    private ExpenseRequest request(String total, SplitType type, List<PayerInput> payers, List<SplitInput> splits) {
        return new ExpenseRequest("Dinner", new MoneyInput("INR", total), payers, type, splits, LocalDate.now());
    }
}

