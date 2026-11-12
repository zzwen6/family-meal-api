package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 某个家庭某天某一餐的「餐次」。第一次有人点餐时自动建，掌厨确认后变为 CONFIRMED。 */
@Entity
@Table(name = "menu")
public class Menu extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    @Column(nullable = false)
    private LocalDate mealDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MealType mealType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MenuStatus status = MenuStatus.OPEN;
    private LocalDateTime deadlineOverride;
    private String note;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime readyAt;
    @Column(nullable = false)
    private boolean stockDeducted;
    @Version
    private long version;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<MenuItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public LocalDate getMealDate() { return mealDate; }
    public void setMealDate(LocalDate mealDate) { this.mealDate = mealDate; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType mealType) { this.mealType = mealType; }
    public MenuStatus getStatus() { return status; }
    public void setStatus(MenuStatus status) { this.status = status; }
    public LocalDateTime getDeadlineOverride() { return deadlineOverride; }
    public void setDeadlineOverride(LocalDateTime deadlineOverride) { this.deadlineOverride = deadlineOverride; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime readyAt) { this.readyAt = readyAt; }
    public boolean isStockDeducted() { return stockDeducted; }
    public void setStockDeducted(boolean stockDeducted) { this.stockDeducted = stockDeducted; }
    public List<MenuItem> getItems() { return items; }
}
