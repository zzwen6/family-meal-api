package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.Enums.MenuItemSource;
import jakarta.persistence.*;

@Entity
@Table(name = "menu_item")
public class MenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id")
    private Menu menu;
    private Long dishId;
    private String customName;
    @Column(nullable = false)
    private int servings;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MenuItemSource source;
    private Long substituteForDishId;
    private String note;
    @Column(nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public Menu getMenu() { return menu; }
    public void setMenu(Menu menu) { this.menu = menu; }
    public Long getDishId() { return dishId; }
    public void setDishId(Long dishId) { this.dishId = dishId; }
    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }
    public int getServings() { return servings; }
    public void setServings(int servings) { this.servings = servings; }
    public MenuItemSource getSource() { return source; }
    public void setSource(MenuItemSource source) { this.source = source; }
    public Long getSubstituteForDishId() { return substituteForDishId; }
    public void setSubstituteForDishId(Long id) { this.substituteForDishId = id; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
