package com.zzwen6.familymeal.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GatheringWishRepository extends JpaRepository<GatheringWish, Long> {
    List<GatheringWish> findByGatheringIdOrderByIdAsc(Long gatheringId);
    Optional<GatheringWish> findByIdAndGatheringId(Long id, Long gatheringId);
    void deleteByParticipantId(Long participantId);
}
