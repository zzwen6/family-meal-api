package com.zzwen6.familymeal.rotation;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "cook_assignment")
public class CookAssignment extends BaseTimeEntity {
    public enum Source { AUTO, MANUAL }

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
    @Column(nullable = false)
    private Long memberId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source;

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public LocalDate getMealDate() { return mealDate; }
    public void setMealDate(LocalDate mealDate) { this.mealDate = mealDate; }
    public MealType getMealType() { return mealType; }
    public void setMealType(MealType mealType) { this.mealType = mealType; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
}
