package com.zzwen6.familymeal.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatheringMenuItemRepository extends JpaRepository<GatheringMenuItem, Long> {
    List<GatheringMenuItem> findByGatheringIdOrderBySortOrderAscIdAsc(Long gatheringId);
    void deleteByGatheringId(Long gatheringId);
}
