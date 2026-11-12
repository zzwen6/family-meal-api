package com.zzwen6.familymeal.inventory;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_item")
public class InventoryItem extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    @Column(nullable = false, length = 64)
    private String name;
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    private String unit;
    @Column(nullable = false)
    private boolean staple;
    private String note;

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean isStaple() { return staple; }
    public void setStaple(boolean staple) { this.staple = staple; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
