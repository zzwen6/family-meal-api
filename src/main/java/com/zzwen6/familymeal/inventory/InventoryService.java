package com.zzwen6.familymeal.inventory;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Names;
import com.zzwen6.familymeal.common.TimeHolder;
import com.zzwen6.familymeal.dish.Amounts;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.inventory.InventoryDtos.*;
import com.zzwen6.familymeal.security.CurrentUser;
import com.zzwen6.familymeal.shopping.ShoppingItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryService {
    private final InventoryItemRepository items;
    private final InventoryLedgerRepository ledger;
    private final FamilyAccess access;

    public InventoryService(InventoryItemRepository items, InventoryLedgerRepository ledger, FamilyAccess access) {
        this.items = items;
        this.ledger = ledger;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<InventoryView> list(Long familyId) {
        access.member(familyId);
        return items.findByFamilyIdOrderByIdAsc(familyId).stream().map(InventoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerView> ledger(Long familyId, int limit) {
        access.member(familyId);
        int n = Math.min(Math.max(limit, 1), 100);
        return ledger.findByFamilyIdOrderByIdDesc(familyId, PageRequest.of(0, n)).stream().map(LedgerView::of).toList();
    }

    @Transactional
    public InventoryView create(Long familyId, InventoryRequest req) {
        access.cook(familyId);
        InventoryItem it = new InventoryItem();
        it.setFamilyId(familyId);
        apply(it, req);
        items.save(it);
        record(familyId, it, LedgerSource.MANUAL, null, it.getAmount() == null ? BigDecimal.ZERO : it.getAmount(),
                "手工登记");
        return InventoryView.of(it);
    }

    @Transactional
    public InventoryView update(Long familyId, Long id, InventoryRequest req) {
        access.cook(familyId);
        InventoryItem it = items.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("库存"));
        BigDecimal before = it.getAmount();
        apply(it, req);
        BigDecimal after = it.getAmount();
        BigDecimal delta = deltaOf(before, after);
        if (delta.signum() != 0) {
            record(familyId, it, LedgerSource.MANUAL, it.getId(), delta, "手工调整");
        }
        return InventoryView.of(it);
    }

    @Transactional
    public void delete(Long familyId, Long id) {
        access.cook(familyId);
        InventoryItem it = items.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("库存"));
        BigDecimal delta = it.getAmount() == null ? BigDecimal.ZERO : it.getAmount().negate();
        record(familyId, it, LedgerSource.MANUAL, it.getId(), delta, "手工删除");
        items.delete(it);
    }

    /**
     * 勾选已买：把该项购买量增加到家庭库存（同名同单位合并）。只入账一次。
     * 购买量为空或 ≤0 时只标记已买，不改数量。
     */
    @Transactional
    public void postPurchase(Long familyId, ShoppingItem item) {
        if (item.getPostedAmount() != null) return; // 已入账
        BigDecimal qty = item.getAmount();
        if (qty == null || qty.signum() <= 0) {
            item.setPostedAmount(BigDecimal.ZERO);
            item.setPostedUnit(item.getUnit());
            return;
        }
        InventoryItem inv = add(familyId, item.getName(), qty, item.getUnit(), false);
        item.setPostedAmount(qty);
        item.setPostedUnit(item.getUnit());
        record(familyId, inv, LedgerSource.PURCHASE, item.getId(), qty, "采购已买「" + item.getName() + "」");
    }

    /** 取消已买：按当时记的入账数量撤回一次。不会无故减成大负数（低于 0 置 0）。 */
    @Transactional
    public void revertPurchase(Long familyId, ShoppingItem item) {
        if (item.getPostedAmount() == null) return;
        BigDecimal qty = item.getPostedAmount();
        String unit = item.getPostedUnit() != null ? item.getPostedUnit() : item.getUnit();
        item.setPostedAmount(null);
        item.setPostedUnit(null);
        if (qty.signum() <= 0) return;
        Deduct d = deduct(familyId, item.getName(), qty, unit);
        if (d.item != null) {
            record(familyId, d.item, LedgerSource.PURCHASE_REVERT, item.getId(), d.actual.negate(),
                    "取消已买「" + item.getName() + "」");
        }
    }

    /**
     * 用掉剩菜：若名称能匹配到库存食材，从库存扣对应数量（扣到 0 为止）。
     * 菜肴类剩菜（对得上菜名但对不上库存食材）不扣。只扣一次。
     */
    @Transactional
    public void deductForLeftover(Leftover leftover) {
        if (leftover.isInventoryDeducted()) return;
        leftover.setInventoryDeducted(true);
        if (leftover.getAmount() == null || leftover.getAmount().signum() <= 0) {
            leftover.setInventoryDeductedAmount(BigDecimal.ZERO);
            return;
        }
        Deduct d = deduct(leftover.getFamilyId(), leftover.getName(), leftover.getAmount(), leftover.getUnit());
        leftover.setInventoryDeductedAmount(d.actual);
        if (d.item == null || d.actual.signum() == 0) return; // 对不上库存食材，不强行扣
        record(leftover.getFamilyId(), d.item, LedgerSource.LEFTOVER_USE, leftover.getId(), d.actual.negate(),
                "用掉剩菜「" + leftover.getName() + "」");
    }

    /**
     * 开饭：按采购清单上记录的「对照时来自库存」的用量扣减，整餐只扣一次。
     * 扣的是库存部分，不含剩菜部分，避免与「用掉剩菜」双扣。
     */
    @Transactional
    public void deductForMeal(Long familyId, Long menuId, List<ShoppingItem> shoppingItems) {
        for (ShoppingItem it : shoppingItems) {
            BigDecimal qty = it.getInventoryOffsetAmount();
            if (qty == null || qty.signum() <= 0) continue;
            Deduct d = deduct(familyId, it.getName(), qty, it.getUnit());
            if (d.item == null || d.actual.signum() == 0) continue;
            record(familyId, d.item, LedgerSource.MEAL_READY, menuId, d.actual.negate(),
                    "开饭用掉家里已有的「" + it.getName() + "」");
        }
    }

    /** 增加库存：同名且单位兼容则合并，否则新建。 */
    InventoryItem add(Long familyId, String name, BigDecimal amount, String unit, boolean staple) {
        InventoryItem it = findMatch(familyId, name, unit);
        if (it == null) {
            it = new InventoryItem();
            it.setFamilyId(familyId);
            it.setName(name.trim());
            it.setAmount(Amounts.normalize(amount));
            it.setUnit(blankToNull(unit));
            it.setStaple(staple);
            items.save(it);
            return it;
        }
        if (it.getAmount() == null) it.setAmount(Amounts.normalize(amount));
        else it.setAmount(Amounts.normalize(it.getAmount().add(amount)));
        return it;
    }

    private Deduct deduct(Long familyId, String name, BigDecimal amount, String unit) {
        List<InventoryItem> matches = new ArrayList<>();
        for (InventoryItem it : items.findByFamilyIdOrderByIdAsc(familyId)) {
            if (Names.same(name, it.getName()) && Names.compatibleUnit(unit, it.getUnit())) matches.add(it);
        }
        if (matches.isEmpty()) return new Deduct(null, BigDecimal.ZERO);
        BigDecimal remain = amount;
        InventoryItem last = null;
        BigDecimal actual = BigDecimal.ZERO;
        for (InventoryItem it : matches) {
            last = it;
            if (it.getAmount() == null) {
                // 适量：记一次流水但不改成负数，视为用完这份对照量
                continue;
            }
            if (it.getAmount().signum() <= 0) continue;
            BigDecimal take = it.getAmount().min(remain);
            it.setAmount(Amounts.normalize(it.getAmount().subtract(take)));
            actual = actual.add(take);
            remain = remain.subtract(take);
            if (remain.signum() <= 0) break;
        }
        if (actual.signum() == 0 && last != null && last.getAmount() == null) {
            return new Deduct(last, BigDecimal.ZERO);
        }
        return new Deduct(last, Amounts.normalize(actual));
    }

    private InventoryItem findMatch(Long familyId, String name, String unit) {
        InventoryItem any = null;
        for (InventoryItem it : items.findByFamilyIdOrderByIdAsc(familyId)) {
            if (!Names.same(name, it.getName()) || !Names.compatibleUnit(unit, it.getUnit())) continue;
            if (Names.sameUnit(unit, it.getUnit())) return it;
            if (any == null) any = it;
        }
        return any;
    }

    private void record(Long familyId, InventoryItem it, LedgerSource type, Long sourceId, BigDecimal delta, String note) {
        InventoryLedger row = new InventoryLedger();
        row.setFamilyId(familyId);
        row.setInventoryItemId(it == null ? null : it.getId());
        row.setSourceType(type);
        row.setSourceId(sourceId);
        row.setName(it == null ? "" : it.getName());
        row.setUnit(it == null ? null : it.getUnit());
        row.setDelta(Amounts.normalize(delta == null ? BigDecimal.ZERO : delta));
        row.setBalanceAfter(it == null ? null : it.getAmount());
        row.setNote(note);
        try {
            row.setCreatedBy(CurrentUser.id());
        } catch (RuntimeException ignored) {
            row.setCreatedBy(null);
        }
        row.setCreatedAt(TimeHolder.now());
        ledger.save(row);
    }

    private static BigDecimal deltaOf(BigDecimal before, BigDecimal after) {
        BigDecimal b = before == null ? BigDecimal.ZERO : before;
        BigDecimal a = after == null ? BigDecimal.ZERO : after;
        return Amounts.normalize(a.subtract(b));
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

    private record Deduct(InventoryItem item, BigDecimal actual) {}
}
