package com.zzwen6.familymeal.nutrition;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.List;

public final class NutritionDtos {
    private NutritionDtos() {}

    /** 热量（千卡）与三大营养素（克），均为估算。 */
    public record Macros(BigDecimal kcal, BigDecimal protein, BigDecimal fat, BigDecimal carb) {
        public static final Macros ZERO = new Macros(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 一道菜或一餐的营养估算。estimated=true 表示按用料估算；override=true 表示掌厨手工覆盖。
     * excluded 为未计入的食材或手写菜及原因。
     */
    public record NutritionEstimate(boolean estimated, boolean override, int servings,
                                    Macros total, Macros perServing, List<String> excluded) {
        public static NutritionEstimate empty(int servings, List<String> excluded) {
            return new NutritionEstimate(true, false, servings, Macros.ZERO, Macros.ZERO, excluded);
        }
    }

    /** 掌厨手工覆盖整道菜（按菜谱默认份数计）的营养。 */
    public record NutritionOverride(
            Boolean enabled,
            @DecimalMin(value = "0", message = "热量不能为负") BigDecimal kcal,
            @DecimalMin(value = "0", message = "蛋白质不能为负") BigDecimal protein,
            @DecimalMin(value = "0", message = "脂肪不能为负") BigDecimal fat,
            @DecimalMin(value = "0", message = "碳水化合物不能为负") BigDecimal carb) {}
}
