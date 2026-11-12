package com.zzwen6.familymeal.template;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeekTemplateRepository extends JpaRepository<WeekTemplate, Long> {
    List<WeekTemplate> findByFamilyIdOrderByIdDesc(Long familyId);
    Optional<WeekTemplate> findByIdAndFamilyId(Long id, Long familyId);

    @Query("select distinct t from WeekTemplate t left join fetch t.items where t.familyId = :familyId order by t.id desc")
    List<WeekTemplate> findWithItems(@Param("familyId") Long familyId);

    @Query("select t from WeekTemplate t left join fetch t.items where t.id = :id and t.familyId = :familyId")
    Optional<WeekTemplate> findWithItemsById(@Param("id") Long id, @Param("familyId") Long familyId);
}
