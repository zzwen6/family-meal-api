package com.zzwen6.familymeal.dish;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import com.zzwen6.familymeal.common.Enums.Difficulty;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dish")
public class Dish extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    @Column(nullable = false, length = 64)
    private String name;
    @Column(nullable = false, length = 32)
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Difficulty difficulty;
    @Column(nullable = false)
    private int durationMinutes;
    @Column(nullable = false)
    private int defaultServings;
    private String coverUrl;
    private String description;
    private String tips;
    @Column(nullable = false)
    private boolean nutritionOverride;
    @Column(precision = 10, scale = 2)
    private BigDecimal nutritionKcal;
    @Column(precision = 10, scale = 2)
    private BigDecimal nutritionProtein;
    @Column(precision = 10, scale = 2)
    private BigDecimal nutritionFat;
    @Column(precision = 10, scale = 2)
    private BigDecimal nutritionCarb;
    @Column(nullable = false)
    private boolean active = true;
    private Long createdBy;

    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo ASC")
    private List<DishStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<DishIngredient> ingredients = new ArrayList<>();

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getDefaultServings() { return defaultServings; }
    public void setDefaultServings(int defaultServings) { this.defaultServings = defaultServings; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTips() { return tips; }
    public void setTips(String tips) { this.tips = tips; }
    public boolean isNutritionOverride() { return nutritionOverride; }
    public void setNutritionOverride(boolean nutritionOverride) { this.nutritionOverride = nutritionOverride; }
    public BigDecimal getNutritionKcal() { return nutritionKcal; }
    public void setNutritionKcal(BigDecimal nutritionKcal) { this.nutritionKcal = nutritionKcal; }
    public BigDecimal getNutritionProtein() { return nutritionProtein; }
    public void setNutritionProtein(BigDecimal nutritionProtein) { this.nutritionProtein = nutritionProtein; }
    public BigDecimal getNutritionFat() { return nutritionFat; }
    public void setNutritionFat(BigDecimal nutritionFat) { this.nutritionFat = nutritionFat; }
    public BigDecimal getNutritionCarb() { return nutritionCarb; }
    public void setNutritionCarb(BigDecimal nutritionCarb) { this.nutritionCarb = nutritionCarb; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public List<DishStep> getSteps() { return steps; }
    public List<DishIngredient> getIngredients() { return ingredients; }
}
