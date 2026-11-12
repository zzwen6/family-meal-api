package com.zzwen6.familymeal.wish;

import com.zzwen6.familymeal.common.Enums.MealType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealWishRepository extends JpaRepository<MealWish, Long> {
    List<MealWish> findByFamilyIdAndMealDateAndMealTypeOrderByIdAsc(Long familyId, LocalDate date, MealType type);
    List<MealWish> findByFamilyIdAndMealDateBetweenOrderByMealDateAscIdAsc(Long familyId, LocalDate from, LocalDate to);
    List<MealWish> findByFamilyIdAndMemberIdAndMealDateBetweenOrderByMealDateAscIdAsc(Long familyId, Long memberId,
                                                                                       LocalDate from, LocalDate to);
    List<MealWish> findByFamilyIdAndMemberIdAndMealDateAndMealType(Long familyId, Long memberId, LocalDate date, MealType type);
    Optional<MealWish> findByIdAndFamilyId(Long id, Long familyId);
}
