package com.zzwen6.familymeal.template;

import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.persistence.*;

@Entity
@Table(name = "week_template_item")
public class WeekTemplateItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private WeekTemplate template;
    /** ISO：1=周一 … 7=周日 */
    @Column(nullable = false)
    private int dayOfWeek;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MealType mealType;
    private Long dishId;
    private String customName;
    @Column(nullable = false)
    private int servings;
    @Column(nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public WeekTemplate getTemplate() { return template; }
    public void setTemplate(WeekTemplate template) { this.template = template; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType mealType) { this.mealType = mealType; }
    public Long getDishId() { return dishId; }
    public void setDishId(Long dishId) { this.dishId = dishId; }
    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }
    public int getServings() { return servings; }
    public void setServings(int servings) { this.servings = servings; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
