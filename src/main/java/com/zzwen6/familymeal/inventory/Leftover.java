package com.zzwen6.familymeal.inventory;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leftover")
public class Leftover extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    private Long menuId;
    @Column(nullable = false, length = 64)
    private String name;
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    private String unit;
    private LocalDate expiresOn;
    @Column(nullable = false)
    private boolean used;
    private LocalDateTime usedAt;
    private Long usedMenuId;
    @Column(nullable = false)
    private boolean inventoryDeducted;
    @Column(precision = 10, scale = 2)
    private BigDecimal inventoryDeductedAmount;
    private String note;

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public Long getUsedMenuId() { return usedMenuId; }
    public void setUsedMenuId(Long usedMenuId) { this.usedMenuId = usedMenuId; }
    public boolean isInventoryDeducted() { return inventoryDeducted; }
    public void setInventoryDeducted(boolean inventoryDeducted) { this.inventoryDeducted = inventoryDeducted; }
    public BigDecimal getInventoryDeductedAmount() { return inventoryDeductedAmount; }
    public void setInventoryDeductedAmount(BigDecimal inventoryDeductedAmount) { this.inventoryDeductedAmount = inventoryDeductedAmount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
