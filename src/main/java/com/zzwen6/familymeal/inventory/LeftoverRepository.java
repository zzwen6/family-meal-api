package com.zzwen6.familymeal.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeftoverRepository extends JpaRepository<Leftover, Long> {
    List<Leftover> findByFamilyIdAndUsedFalseOrderByExpiresOnAscIdAsc(Long familyId);
    List<Leftover> findByFamilyIdOrderByUsedAscExpiresOnAscIdAsc(Long familyId);
    Optional<Leftover> findByIdAndFamilyId(Long id, Long familyId);
    List<Leftover> findByFamilyIdAndIdIn(Long familyId, List<Long> ids);
}
