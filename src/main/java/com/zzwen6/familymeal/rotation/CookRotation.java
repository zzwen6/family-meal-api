package com.zzwen6.familymeal.rotation;

import com.zzwen6.familymeal.common.BaseTimeEntity;
import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "cook_rotation")
public class CookRotation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 64)
    private String mealTypes = "DINNER";

    @OneToMany(mappedBy = "rotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<CookRotationMember> members = new ArrayList<>();

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMealTypesRaw() { return mealTypes; }
    public void setMealTypesRaw(String mealTypes) { this.mealTypes = mealTypes; }
    public List<CookRotationMember> getMembers() { return members; }

    public List<MealType> mealTypeList() {
        if (mealTypes == null || mealTypes.isBlank()) return List.of(MealType.DINNER);
        return Arrays.stream(mealTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(MealType::valueOf)
                .toList();
    }

    public void setMealTypeList(List<MealType> types) {
        if (types == null || types.isEmpty()) {
            this.mealTypes = "DINNER";
            return;
        }
        this.mealTypes = String.join(",", types.stream().map(Enum::name).distinct().toList());
    }
}
