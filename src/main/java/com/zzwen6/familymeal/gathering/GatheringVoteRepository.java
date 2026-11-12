package com.zzwen6.familymeal.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GatheringVoteRepository extends JpaRepository<GatheringVote, Long> {
    List<GatheringVote> findByGatheringId(Long gatheringId);
    Optional<GatheringVote> findByWishIdAndParticipantId(Long wishId, Long participantId);
    long countByWishId(Long wishId);
}
