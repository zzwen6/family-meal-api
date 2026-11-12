package com.zzwen6.familymeal.inventory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, Long> {
    List<InventoryLedger> findByFamilyIdAndSourceTypeAndSourceId(Long familyId, LedgerSource sourceType, Long sourceId);
    List<InventoryLedger> findByFamilyIdOrderByIdDesc(Long familyId, Pageable pageable);
}
