package com.zzwen6.familymeal.dish;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DishRepository extends JpaRepository<Dish, Long> {
    Optional<Dish> findByIdAndFamilyId(Long id, Long familyId);

    List<Dish> findByFamilyIdAndIdIn(Long familyId, Collection<Long> ids);

    @Query("""
            select d from Dish d where d.familyId = :familyId
              and (:includeInactive = true or d.active = true)
              and (:category is null or d.category = :category)
              and (:keyword is null or d.name like concat('%', :keyword, '%'))
            order by d.category asc, d.name asc""")
    List<Dish> search(@Param("familyId") Long familyId, @Param("category") String category,
                      @Param("keyword") String keyword, @Param("includeInactive") boolean includeInactive);

    @Query("select distinct d.category from Dish d where d.familyId = :familyId and d.active = true order by d.category")
    List<String> categories(@Param("familyId") Long familyId);
}
