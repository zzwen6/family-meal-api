package com.zzwen6.familymeal.family;

import com.zzwen6.familymeal.common.Enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    Optional<FamilyMember> findByFamilyIdAndUserId(Long familyId, Long userId);
    Optional<FamilyMember> findByIdAndFamilyId(Long id, Long familyId);
    List<FamilyMember> findByFamilyIdOrderByIdAsc(Long familyId);
    List<FamilyMember> findByUserIdOrderByIdAsc(Long userId);
    long countByFamilyIdAndRole(Long familyId, Role role);
}
