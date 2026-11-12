package com.zzwen6.familymeal.shopping;

import com.zzwen6.familymeal.common.Enums.IngredientKind;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "shopping_item")
public class ShoppingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id")
    private ShoppingList list;
    @Column(nullable = false, length = 64)
    private String name;
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    private String unit;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IngredientKind kind;
    @Column(nullable = false)
    private boolean staple;
    @Column(nullable = false)
    private boolean toBuy = true;
    @Column(nullable = false)
    private boolean purchased;
    private String sourceDishes;
    private String note;
    @Column(nullable = false)
    private boolean manualToBuy;
    @Column(nullable = false)
    private int sortOrder;

    /** 合并键：同名同单位视为同一样东西。 */
    public String mergeKey() {
        return key(name, unit);
    }

    public static String key(String name, String unit) {
        return name.trim().toLowerCase() + "|" + (unit == null ? "" : unit.trim().toLowerCase());
    }

    public Long getId() { return id; }
    public ShoppingList getList() { return list; }
    public void setList(ShoppingList list) { this.list = list; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public IngredientKind getKind() { return kind; }
    public void setKind(IngredientKind kind) { this.kind = kind; }
    public boolean isStaple() { return staple; }
    public void setStaple(boolean staple) { this.staple = staple; }
    public boolean isToBuy() { return toBuy; }
    public void setToBuy(boolean toBuy) { this.toBuy = toBuy; }
    public boolean isPurchased() { return purchased; }
    public void setPurchased(boolean purchased) { this.purchased = purchased; }
    public String getSourceDishes() { return sourceDishes; }
    public void setSourceDishes(String sourceDishes) { this.sourceDishes = sourceDishes; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isManualToBuy() { return manualToBuy; }
    public void setManualToBuy(boolean manualToBuy) { this.manualToBuy = manualToBuy; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
