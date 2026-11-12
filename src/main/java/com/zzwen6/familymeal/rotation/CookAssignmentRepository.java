package com.zzwen6.familymeal.rotation;

import com.zzwen6.familymeal.common.Enums.MealType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CookAssignmentRepository extends JpaRepository<CookAssignment, Long> {
    Optional<CookAssignment> findByFamilyIdAndMealDateAndMealType(Long familyId, LocalDate date, MealType type);
    List<CookAssignment> findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(
            Long familyId, LocalDate from, LocalDate to);
}
