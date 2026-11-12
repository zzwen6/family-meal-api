package com.zzwen6.familymeal.inventory;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Names;
import com.zzwen6.familymeal.common.TimeHolder;
import com.zzwen6.familymeal.dish.Amounts;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.inventory.InventoryDtos.*;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.rotation.KitchenDuty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeftoverService {
    private final LeftoverRepository leftovers;
    private final FamilyAccess access;
    private final MenuRepository menus;
    private final KitchenDuty duty;

    public LeftoverService(LeftoverRepository leftovers, FamilyAccess access, MenuRepository menus, KitchenDuty duty) {
        this.leftovers = leftovers;
        this.access = access;
        this.menus = menus;
        this.duty = duty;
    }

    @Transactional(readOnly = true)
    public List<LeftoverView> list(Long familyId, Boolean used) {
        access.member(familyId);
        List<Leftover> list = Boolean.FALSE.equals(used)
                ? leftovers.findByFamilyIdAndUsedFalseOrderByExpiresOnAscIdAsc(familyId)
                : leftovers.findByFamilyIdOrderByUsedAscExpiresOnAscIdAsc(familyId);
        if (Boolean.TRUE.equals(used)) list = list.stream().filter(Leftover::isUsed).toList();
        return list.stream().map(LeftoverView::of).toList();
    }

    @Transactional
    public LeftoverView create(Long familyId, LeftoverRequest req) {
        FamilyMember me = access.member(familyId);
        requireWrite(me, req.menuId());
        if (req.menuId() != null) {
            menus.findByIdAndFamilyId(req.menuId(), familyId).orElseThrow(() -> BizException.notFound("菜单"));
        }
        Leftover l = new Leftover();
        l.setFamilyId(familyId);
        apply(l, req);
        leftovers.save(l);
        return LeftoverView.of(l);
    }

    @Transactional
    public LeftoverView update(Long familyId, Long id, LeftoverRequest req) {
        FamilyMember me = access.member(familyId);
        Leftover l = leftovers.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("剩菜"));
        requireWrite(me, l.getMenuId() != null ? l.getMenuId() : req.menuId());
        apply(l, req);
        return LeftoverView.of(l);
    }

    @Transactional
    public LeftoverView markUsed(Long familyId, Long id, Long usedMenuId) {
        FamilyMember me = access.member(familyId);
        Leftover l = leftovers.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("剩菜"));
        requireWrite(me, l.getMenuId() != null ? l.getMenuId() : usedMenuId);
        use(l, usedMenuId);
        return LeftoverView.of(l);
    }

    @Transactional
    public void delete(Long familyId, Long id) {
        FamilyMember me = access.member(familyId);
        Leftover l = leftovers.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("剩菜"));
        requireWrite(me, l.getMenuId());
        leftovers.delete(l);
    }

    /** 确认菜单时：把明确勾选的剩菜标为已用。 */
    @Transactional
    public void useAll(Long familyId, List<Long> ids, Long menuId) {
        if (ids == null || ids.isEmpty()) return;
        for (Leftover l : leftovers.findByFamilyIdAndIdIn(familyId, ids)) {
            if (!l.isUsed()) use(l, menuId);
        }
    }

    /** 确认菜单时：名称对得上的未用完剩菜也标为已用（itemNames 已是菜谱名或手写菜名）。 */
    @Transactional
    public void autoUseMatching(Long familyId, Long menuId, List<String> itemNames) {
        if (itemNames == null || itemNames.isEmpty()) return;
        List<String> names = itemNames.stream().map(Names::normalize).filter(s -> !s.isEmpty()).toList();
        if (names.isEmpty()) return;
        for (Leftover l : leftovers.findByFamilyIdAndUsedFalseOrderByExpiresOnAscIdAsc(familyId)) {
            if (names.contains(Names.normalize(l.getName()))) use(l, menuId);
        }
    }

    private void use(Leftover l, Long usedMenuId) {
        l.setUsed(true);
        l.setUsedAt(TimeHolder.now());
        l.setUsedMenuId(usedMenuId);
    }

    private void requireWrite(FamilyMember me, Long menuId) {
        if (me.getRole().canCook()) return;
        if (menuId != null) {
            Menu menu = menus.findByIdAndFamilyId(menuId, me.getFamilyId()).orElse(null);
            if (menu != null && duty.canRunKitchen(me, menu.getMealDate(), menu.getMealType())) return;
            if (menu != null && me.getId().equals(menu.getConfirmedBy())) return;
        }
        throw BizException.forbidden("只有掌厨或管理员可以登记剩菜");
    }

    private void apply(Leftover l, LeftoverRequest req) {
        l.setMenuId(req.menuId());
        l.setName(req.name().trim());
        l.setAmount(Amounts.normalize(req.amount()));
        l.setUnit(req.unit() == null || req.unit().isBlank() ? null : req.unit().trim());
        l.setExpiresOn(req.expiresOn());
        l.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
    }

}
