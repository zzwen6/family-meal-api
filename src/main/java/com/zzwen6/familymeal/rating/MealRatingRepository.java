package com.zzwen6.familymeal.rating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MealRatingRepository extends JpaRepository<MealRating, Long> {
    Optional<MealRating> findByMenuIdAndMemberId(Long menuId, Long memberId);
    List<MealRating> findByFamilyIdAndMenuIdOrderByIdAsc(Long familyId, Long menuId);
    List<MealRating> findByFamilyIdAndMenuIdIn(Long familyId, Collection<Long> menuIds);
}
