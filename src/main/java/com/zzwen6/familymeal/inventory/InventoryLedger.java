package com.zzwen6.familymeal.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_ledger")
public class InventoryLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    private Long inventoryItemId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LedgerSource sourceType;
    private Long sourceId;
    @Column(nullable = false, length = 64)
    private String name;
    private String unit;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal delta;
    @Column(precision = 10, scale = 2)
    private BigDecimal balanceAfter;
    private String note;
    private Long createdBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public Long getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(Long inventoryItemId) { this.inventoryItemId = inventoryItemId; }
    public LedgerSource getSourceType() { return sourceType; }
    public void setSourceType(LedgerSource sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getDelta() { return delta; }
    public void setDelta(BigDecimal delta) { this.delta = delta; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
