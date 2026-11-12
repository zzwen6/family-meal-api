package com.zzwen6.familymeal.template;

import com.zzwen6.familymeal.common.Enums.MealType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MealPlanItemRepository extends JpaRepository<MealPlanItem, Long> {
    List<MealPlanItem> findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAscSortOrderAscIdAsc(
            Long familyId, LocalDate from, LocalDate to);
    List<MealPlanItem> findByFamilyIdAndMealDateAndMealTypeOrderBySortOrderAscIdAsc(
            Long familyId, LocalDate date, MealType type);
    boolean existsByFamilyIdAndMealDateAndMealType(Long familyId, LocalDate date, MealType type);
    void deleteByFamilyIdAndMealDateAndMealType(Long familyId, LocalDate date, MealType type);
}
