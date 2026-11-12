package com.zzwen6.familymeal.wish;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import com.zzwen6.familymeal.common.Enums.Intensity;
import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "meal_wish")
public class MealWish extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    @Column(nullable = false)
    private Long memberId;
    @Column(nullable = false)
    private LocalDate mealDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MealType mealType;
    private Long dishId;
    private String freeText;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Intensity intensity;
    @Column(nullable = false)
    private int servings = 1;
    @Column(nullable = false)
    private boolean notEatingHome;
    private String note;

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public LocalDate getMealDate() { return mealDate; }
    public void setMealDate(LocalDate mealDate) { this.mealDate = mealDate; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType mealType) { this.mealType = mealType; }
    public Long getDishId() { return dishId; }
    public void setDishId(Long dishId) { this.dishId = dishId; }
    public String getFreeText() { return freeText; }
    public void setFreeText(String freeText) { this.freeText = freeText; }
    public Intensity getIntensity() { return intensity; }
    public void setIntensity(Intensity intensity) { this.intensity = intensity; }
    public int getServings() { return servings; }
    public void setServings(int servings) { this.servings = servings; }
    public boolean isNotEatingHome() { return notEatingHome; }
    public void setNotEatingHome(boolean notEatingHome) { this.notEatingHome = notEatingHome; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
