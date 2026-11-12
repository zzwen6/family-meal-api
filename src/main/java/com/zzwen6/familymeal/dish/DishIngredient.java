package com.zzwen6.familymeal.dish;

import com.zzwen6.familymeal.common.Enums.IngredientKind;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "dish_ingredient")
public class DishIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dish_id")
    private Dish dish;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IngredientKind kind;
    @Column(nullable = false, length = 64)
    private String name;
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    private String unit;
    @Column(nullable = false)
    private boolean staple;
    private String note;
    @Column(nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public Dish getDish() { return dish; }
    public void setDish(Dish dish) { this.dish = dish; }
    public IngredientKind getKind() { return kind; }
    public void setKind(IngredientKind kind) { this.kind = kind; }
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
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
