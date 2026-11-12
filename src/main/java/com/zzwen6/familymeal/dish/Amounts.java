package com.zzwen6.familymeal.dish;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** 份量换算工具：按「需要份数 / 菜谱默认份数」等比缩放，并按单位取一个好读的数。 */
public final class Amounts {
    private Amounts() {}

    /** 按个数计的单位，换算后向上取整（买 2.67 罐可乐没有意义）。 */
    private static final Set<String> COUNT_UNITS = Set.of(
            "个", "只", "片", "罐", "根", "瓣", "颗", "块", "条", "把", "包", "袋", "盒", "头", "棵", "张", "粒", "枚",
            "瓶", "碗", "杯", "串", "朵", "支", "份", "斤");
    /** 重量/体积单位，大于 10 时取整，避免 2666.67g 这种数。 */
    private static final Set<String> FINE_UNITS = Set.of("g", "克", "ml", "毫升", "kg", "千克", "l", "升");

    public static BigDecimal scale(BigDecimal amount, int servings, int defaultServings, String unit) {
        if (amount == null) return null;
        BigDecimal v = defaultServings <= 0 || servings == defaultServings ? amount
                : amount.multiply(BigDecimal.valueOf(servings)).divide(BigDecimal.valueOf(defaultServings), 4, RoundingMode.HALF_UP);
        return friendly(v, unit);
    }

    /** 缩放但不按单位取整（合并采购清单时先累加精确值，最后再 friendly）。 */
    public static BigDecimal scaleRaw(BigDecimal amount, int servings, int defaultServings) {
        if (amount == null) return null;
        if (defaultServings <= 0 || servings == defaultServings) return amount;
        return amount.multiply(BigDecimal.valueOf(servings)).divide(BigDecimal.valueOf(defaultServings), 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal friendly(BigDecimal v, String unit) {
        if (v == null) return null;
        String u = unit == null ? "" : unit.trim().toLowerCase();
        if (COUNT_UNITS.contains(u)) {
            // 斤这种允许半斤
            if ("斤".equals(u)) return normalize(v.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.CEILING)
                    .divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY));
            return normalize(v.setScale(0, RoundingMode.CEILING));
        }
        if (FINE_UNITS.contains(u) && v.compareTo(BigDecimal.TEN) > 0) return normalize(v.setScale(0, RoundingMode.HALF_UP));
        if ("勺".equals(u) || "汤匙".equals(u) || "茶匙".equals(u)) {
            return normalize(v.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY));
        }
        return normalize(v);
    }

    public static BigDecimal normalize(BigDecimal v) {
        if (v == null) return null;
        BigDecimal s = v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }
}
