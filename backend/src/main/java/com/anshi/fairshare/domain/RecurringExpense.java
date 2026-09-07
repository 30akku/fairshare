package com.anshi.fairshare.domain;

import com.anshi.fairshare.domain.DomainEnums.RecurrenceFrequency;
import com.anshi.fairshare.domain.DomainEnums.SplitType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;
    @Column(nullable = false, length = 200) private String description;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal total;
    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payer_configuration", nullable = false, columnDefinition = "jsonb")
    private String payerConfiguration;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "split_configuration", nullable = false, columnDefinition = "jsonb")
    private String splitConfiguration;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceFrequency frequency;
    @Column(name = "next_run_date", nullable = false) private LocalDate nextRunDate;
    @Column(name = "anchor_day", nullable = false) private int anchorDay;
    @Column(name = "zone_id", nullable = false, length = 60) private String zoneId;
    @Column(nullable = false) private boolean active = true;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;
    @Version private long version;

    public ExpenseGroup getGroup() { return group; }
    public void setGroup(ExpenseGroup group) { this.group = group; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public SplitType getSplitType() { return splitType; }
    public void setSplitType(SplitType splitType) { this.splitType = splitType; }
    public String getPayerConfiguration() { return payerConfiguration; }
    public void setPayerConfiguration(String payerConfiguration) { this.payerConfiguration = payerConfiguration; }
    public String getSplitConfiguration() { return splitConfiguration; }
    public void setSplitConfiguration(String splitConfiguration) { this.splitConfiguration = splitConfiguration; }
    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }
    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }
    public int getAnchorDay() { return anchorDay; }
    public void setAnchorDay(int anchorDay) { this.anchorDay = anchorDay; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public AppUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
    public long getVersion() { return version; }
}

