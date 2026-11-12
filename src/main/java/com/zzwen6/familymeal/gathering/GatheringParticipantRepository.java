package com.zzwen6.familymeal.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GatheringParticipantRepository extends JpaRepository<GatheringParticipant, Long> {
    List<GatheringParticipant> findByGatheringIdOrderByIdAsc(Long gatheringId);
    Optional<GatheringParticipant> findByGatheringIdAndUserId(Long gatheringId, Long userId);
    Optional<GatheringParticipant> findByIdAndGatheringId(Long id, Long gatheringId);
    List<GatheringParticipant> findByUserId(Long userId);
}
