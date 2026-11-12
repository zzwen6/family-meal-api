package com.zzwen6.familymeal.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GatheringRepository extends JpaRepository<Gathering, Long> {
    Optional<Gathering> findByInviteToken(String token);
    boolean existsByInviteToken(String token);
    List<Gathering> findByIdInOrderByMealDateDescIdDesc(Collection<Long> ids);
    List<Gathering> findByHostFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(Long familyId, LocalDate from, LocalDate to);
}
