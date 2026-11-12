package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    Optional<Menu> findByFamilyIdAndMealDateAndMealType(Long familyId, LocalDate date, MealType type);
    Optional<Menu> findByIdAndFamilyId(Long id, Long familyId);
    List<Menu> findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(Long familyId, LocalDate from, LocalDate to);
    List<Menu> findByStatusAndMealDateBetween(MenuStatus status, LocalDate from, LocalDate to);

    @Query("select distinct m from Menu m left join fetch m.items where m.familyId = :familyId and m.mealDate between :from and :to order by m.mealDate, m.mealType")
    List<Menu> findWithItems(@Param("familyId") Long familyId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
