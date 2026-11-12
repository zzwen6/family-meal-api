package com.zzwen6.familymeal.dish;

import com.zzwen6.familymeal.common.Names;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

/**
 * 把常见中式用量单位换算成克。能换则换，不能换（没有单件克数的「个 / 片」等）返回 empty。
 * 体积按近似密度：水、酱油类 1 g/ml，食用油 0.92 g/ml。
 */
public final class Units {
    private Units() {}

    private static final Set<String> OIL = Set.of("油", "食用油", "菜籽油", "花生油", "大豆油", "玉米油",
            "调和油", "色拉油", "橄榄油", "香油", "麻油", "芝麻油", "猪油", "板油", "黄油", "牛油");

    public static Optional<BigDecimal> toGrams(BigDecimal amount, String unit, BigDecimal pieceGrams, String ingredientName) {
        if (amount == null) return Optional.empty();
        String u = Names.normalizeUnit(unit);
        if (u.isEmpty()) {
            if (pieceGrams != null && pieceGrams.signum() > 0) {
                return Optional.of(amount.multiply(pieceGrams));
            }
            return Optional.empty();
        }
        return switch (u) {
            case "g" -> Optional.of(amount);
            case "kg" -> Optional.of(amount.multiply(BigDecimal.valueOf(1000)));
            case "斤" -> Optional.of(amount.multiply(BigDecimal.valueOf(500)));
            case "两" -> Optional.of(amount.multiply(BigDecimal.valueOf(50)));
            case "ml" -> Optional.of(amount.multiply(density(ingredientName)));
            case "l" -> Optional.of(amount.multiply(BigDecimal.valueOf(1000)).multiply(density(ingredientName)));
            case "勺" -> Optional.of(amount.multiply(BigDecimal.valueOf(15)).multiply(density(ingredientName)));
            case "茶匙" -> Optional.of(amount.multiply(BigDecimal.valueOf(5)).multiply(density(ingredientName)));
            default -> {
                if (pieceGrams != null && pieceGrams.signum() > 0) {
                    yield Optional.of(amount.multiply(pieceGrams));
                }
                yield Optional.empty();
            }
        };
    }

    public static BigDecimal density(String name) {
        String n = Names.normalize(name);
        for (String oil : OIL) {
            if (n.contains(Names.normalize(oil))) return BigDecimal.valueOf(0.92);
        }
        return BigDecimal.ONE;
    }

    public static BigDecimal scalePer100g(BigDecimal per100g, BigDecimal grams) {
        if (per100g == null || grams == null) return BigDecimal.ZERO;
        return per100g.multiply(grams).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }
}
