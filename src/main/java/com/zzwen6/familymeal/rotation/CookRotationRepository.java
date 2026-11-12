package com.zzwen6.familymeal.rotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CookRotationRepository extends JpaRepository<CookRotation, Long> {
    Optional<CookRotation> findByFamilyId(Long familyId);
}
