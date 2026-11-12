package com.zzwen6.familymeal.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByFamilyIdOrderByIdAsc(Long familyId);
    Optional<InventoryItem> findByIdAndFamilyId(Long id, Long familyId);
}
