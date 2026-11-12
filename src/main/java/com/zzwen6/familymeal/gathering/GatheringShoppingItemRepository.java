package com.zzwen6.familymeal.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GatheringShoppingItemRepository extends JpaRepository<GatheringShoppingItem, Long> {
    List<GatheringShoppingItem> findByGatheringIdOrderBySortOrderAscIdAsc(Long gatheringId);
    Optional<GatheringShoppingItem> findByIdAndGatheringId(Long id, Long gatheringId);
}
