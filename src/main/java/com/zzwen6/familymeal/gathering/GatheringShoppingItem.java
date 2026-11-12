package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.TimeHolder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 聚餐采购项，由发起人手填（菜名是快照，不能套用家庭菜谱的用料）。
 * baseServings 不为空时，amount 表示「baseServings 人份」的用量，展示时按出席总人数换算。
 */
@Entity
@Table(name = "gathering_shopping_item")
public class GatheringShoppingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long gatheringId;
    @Column(nullable = false, length = 64)
    private String name;
    private BigDecimal amount;
    private String unit;
    private Integer baseServings;
    private String forDish;
    private String note;
    @Column(nullable = false)
    private boolean purchased;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = TimeHolder.now();
    }

    public Long getId() { return id; }
    public Long getGatheringId() { return gatheringId; }
    public void setGatheringId(Long gatheringId) { this.gatheringId = gatheringId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getBaseServings() { return baseServings; }
    public void setBaseServings(Integer baseServings) { this.baseServings = baseServings; }
    public String getForDish() { return forDish; }
    public void setForDish(String forDish) { this.forDish = forDish; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isPurchased() { return purchased; }
    public void setPurchased(boolean purchased) { this.purchased = purchased; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
