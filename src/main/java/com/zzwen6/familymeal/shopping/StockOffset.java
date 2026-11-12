package com.zzwen6.familymeal.shopping;

import com.zzwen6.familymeal.common.Names;
import com.zzwen6.familymeal.dish.Amounts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 采购对照：按规范化名称匹配库存和未用完剩菜，计算建议购买量。
 * 只做计算，不改库存或剩菜数量，因此重新生成不会重复扣减。
 * 剩菜优先于库存（更可能先过期）。
 */
public final class StockOffset {
    private StockOffset() {}

    public enum Kind { LEFTOVER, INVENTORY }

    public record PoolItem(Kind kind, String name, BigDecimal amount, String unit) {}

    public record Result(BigDecimal remaining, boolean fullyCovered, String note, boolean applied,
                         BigDecimal leftoverUsed, BigDecimal inventoryUsed) {
        public static Result none(BigDecimal needed) {
            return new Result(needed, false, null, false, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public static Result apply(String itemName, BigDecimal needed, String itemUnit, List<PoolItem> pool) {
        List<PoolItem> matched = new ArrayList<>();
        boolean unitMismatch = false;
        for (PoolItem p : pool) {
            if (!Names.same(itemName, p.name())) continue;
            if (Names.compatibleUnit(itemUnit, p.unit())) matched.add(p);
            else unitMismatch = true;
        }
        if (matched.isEmpty()) {
            if (unitMismatch) {
                return new Result(needed, false, "家里有同名的库存或剩菜，但单位不同，请核对", true, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            return Result.none(needed);
        }
        BigDecimal leftoverAmt = sum(matched, Kind.LEFTOVER);
        BigDecimal inventoryAmt = sum(matched, Kind.INVENTORY);
        boolean leftoverUnknown = hasUnknown(matched, Kind.LEFTOVER);
        boolean inventoryUnknown = hasUnknown(matched, Kind.INVENTORY);
        boolean anyUnknown = leftoverUnknown || inventoryUnknown;
        BigDecimal known = leftoverAmt.add(inventoryAmt);

        if (needed == null || anyUnknown) {
            // 用量未知或家里有「适量」：建议不买，但开饭时无法准确扣库存（inventoryUsed=0）
            return new Result(BigDecimal.ZERO, true,
                    "家里已有" + detail(leftoverAmt, leftoverUnknown, inventoryAmt, inventoryUnknown), true,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal leftoverUsed = leftoverAmt.min(needed);
        BigDecimal afterLeftover = needed.subtract(leftoverUsed);
        BigDecimal inventoryUsed = inventoryAmt.min(afterLeftover);
        if (known.compareTo(needed) >= 0) {
            return new Result(BigDecimal.ZERO, true, "家里已有" + detail(leftoverAmt, false, inventoryAmt, false), true,
                    leftoverUsed, inventoryUsed);
        }
        BigDecimal remaining = Amounts.friendly(needed.subtract(leftoverUsed).subtract(inventoryUsed), itemUnit);
        return new Result(remaining, false,
                "已用" + usedPhrase(leftoverAmt, inventoryAmt) + "，还需买 " + label(remaining, itemUnit), true,
                leftoverUsed, inventoryUsed);
    }

    /**
     * 从库存池中扣掉本清单已经入账的采购量，避免「刚勾已买加进库存，再生成又当成家里已有」。
     * 只动 INVENTORY，不动剩菜。
     */
    public static void consumeInventory(List<PoolItem> pool, String name, BigDecimal qty, String unit) {
        if (pool == null || qty == null || qty.signum() <= 0) return;
        BigDecimal remain = qty;
        for (int i = 0; i < pool.size(); i++) {
            PoolItem p = pool.get(i);
            if (p.kind() != Kind.INVENTORY) continue;
            if (!Names.same(name, p.name()) || !Names.compatibleUnit(unit, p.unit())) continue;
            if (p.amount() == null) continue;
            BigDecimal take = p.amount().min(remain);
            pool.set(i, new PoolItem(p.kind(), p.name(), p.amount().subtract(take), p.unit()));
            remain = remain.subtract(take);
            if (remain.signum() <= 0) return;
        }
    }

    private static BigDecimal sum(List<PoolItem> list, Kind kind) {
        BigDecimal s = BigDecimal.ZERO;
        for (PoolItem p : list) {
            if (p.kind() == kind && p.amount() != null) s = s.add(p.amount());
        }
        return s;
    }

    private static boolean hasUnknown(List<PoolItem> list, Kind kind) {
        return list.stream().anyMatch(p -> p.kind() == kind && p.amount() == null);
    }

    private static String detail(BigDecimal leftover, boolean leftoverUnknown, BigDecimal inventory, boolean inventoryUnknown) {
        StringBuilder sb = new StringBuilder("（");
        boolean any = false;
        if (leftoverUnknown || leftover.signum() > 0) {
            sb.append("剩菜 ").append(leftoverUnknown ? "适量" : leftover.stripTrailingZeros().toPlainString());
            any = true;
        }
        if (inventoryUnknown || inventory.signum() > 0) {
            if (any) sb.append(" + ");
            sb.append("库存 ").append(inventoryUnknown ? "适量" : inventory.stripTrailingZeros().toPlainString());
        }
        sb.append("）");
        return sb.toString();
    }

    private static String usedPhrase(BigDecimal leftover, BigDecimal inventory) {
        StringBuilder sb = new StringBuilder();
        if (leftover.signum() > 0) sb.append("剩菜 ").append(leftover.stripTrailingZeros().toPlainString());
        if (inventory.signum() > 0) {
            if (!sb.isEmpty()) sb.append("、");
            sb.append("库存 ").append(inventory.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    private static String label(BigDecimal amount, String unit) {
        String u = unit == null ? "" : unit;
        return amount.stripTrailingZeros().toPlainString() + u;
    }
}
