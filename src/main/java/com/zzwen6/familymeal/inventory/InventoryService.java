package com.zzwen6.familymeal.inventory;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.dish.Amounts;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.inventory.InventoryDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {
    private final InventoryItemRepository items;
    private final FamilyAccess access;

    public InventoryService(InventoryItemRepository items, FamilyAccess access) {
        this.items = items;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<InventoryView> list(Long familyId) {
        access.member(familyId);
        return items.findByFamilyIdOrderByIdAsc(familyId).stream().map(InventoryView::of).toList();
    }

    @Transactional
    public InventoryView create(Long familyId, InventoryRequest req) {
        access.cook(familyId);
        InventoryItem it = new InventoryItem();
        it.setFamilyId(familyId);
        apply(it, req);
        items.save(it);
        return InventoryView.of(it);
    }

    @Transactional
    public InventoryView update(Long familyId, Long id, InventoryRequest req) {
        access.cook(familyId);
        InventoryItem it = items.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("库存"));
        apply(it, req);
        return InventoryView.of(it);
    }

    @Transactional
    public void delete(Long familyId, Long id) {
        access.cook(familyId);
        InventoryItem it = items.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("库存"));
        items.delete(it);
    }

    private static void apply(InventoryItem it, InventoryRequest req) {
        it.setName(req.name().trim());
        it.setAmount(Amounts.normalize(req.amount()));
        it.setUnit(blankToNull(req.unit()));
        it.setStaple(Boolean.TRUE.equals(req.staple()));
        it.setNote(blankToNull(req.note()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
