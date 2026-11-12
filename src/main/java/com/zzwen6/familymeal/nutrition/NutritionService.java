package com.zzwen6.familymeal.nutrition;

import com.zzwen6.familymeal.dish.Amounts;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishIngredient;
import com.zzwen6.familymeal.dish.Units;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuItem;
import com.zzwen6.familymeal.nutrition.NutritionDtos.Macros;
import com.zzwen6.familymeal.nutrition.NutritionDtos.NutritionEstimate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 按菜谱用料估算营养。这是估算，不是检测或官方认证。
 * 掌厨手工覆盖整道菜时以覆盖值为准；找不到的食材、「适量」且无重量、单位无法换算的，不计入并在 excluded 列出。
 * 聚餐不走这里。
 */
@Service
public class NutritionService {
    private final NutritionTable table;

    public NutritionService(NutritionTable table) {
        this.table = table;
    }

    public NutritionEstimate ofDish(Dish dish, int servings) {
        int s = Math.max(1, servings);
        if (dish.isNutritionOverride()) {
            Macros base = macros(dish.getNutritionKcal(), dish.getNutritionProtein(),
                    dish.getNutritionFat(), dish.getNutritionCarb());
            Macros total = scale(base, s, dish.getDefaultServings());
            return new NutritionEstimate(false, true, s, total, perServing(total, s), List.of());
        }
        return estimate(dish.getIngredients(), dish.getDefaultServings(), s);
    }

    /**
     * 已确认菜单的本餐汇总：只含有菜谱的菜；手写菜没有用料则跳过并注明。
     * 聚餐菜单不要调用。
     */
    public NutritionEstimate ofMenu(Menu menu, Map<Long, Dish> dishes) {
        if (menu.getItems().isEmpty()) {
            return NutritionEstimate.empty(0, List.of());
        }
        BigDecimal kcal = BigDecimal.ZERO, protein = BigDecimal.ZERO, fat = BigDecimal.ZERO, carb = BigDecimal.ZERO;
        List<String> excluded = new ArrayList<>();
        int totalServings = 0;
        boolean anyOverride = false;
        boolean anyEstimated = false;
        for (MenuItem mi : menu.getItems()) {
            totalServings += mi.getServings();
            if (mi.getDishId() == null) {
                String name = mi.getCustomName() == null || mi.getCustomName().isBlank() ? "未命名" : mi.getCustomName();
                excluded.add("手写菜「" + name + "」（没有用料）");
                continue;
            }
            Dish d = dishes.get(mi.getDishId());
            if (d == null) {
                excluded.add("菜谱已不在（#" + mi.getDishId() + "）");
                continue;
            }
            NutritionEstimate one = ofDish(d, mi.getServings());
            anyOverride |= one.override();
            anyEstimated |= one.estimated();
            kcal = kcal.add(one.total().kcal());
            protein = protein.add(one.total().protein());
            fat = fat.add(one.total().fat());
            carb = carb.add(one.total().carb());
            for (String e : one.excluded()) {
                excluded.add(d.getName() + "：" + e);
            }
        }
        int head = Math.max(1, totalServings);
        Macros total = round(new Macros(kcal, protein, fat, carb));
        boolean estimated = anyEstimated || !anyOverride;
        return new NutritionEstimate(estimated, anyOverride && !anyEstimated, head, total, perServing(total, head),
                List.copyOf(excluded));
    }

    NutritionEstimate estimate(List<DishIngredient> ingredients, int defaultServings, int servings) {
        BigDecimal kcal = BigDecimal.ZERO, protein = BigDecimal.ZERO, fat = BigDecimal.ZERO, carb = BigDecimal.ZERO;
        List<String> excluded = new ArrayList<>();
        if (ingredients != null) {
            for (DishIngredient ing : ingredients) {
                BigDecimal scaled = Amounts.scaleRaw(ing.getAmount(), servings, defaultServings);
                Acc acc = accumulate(ing.getName(), scaled, ing.getUnit());
                if (acc.excluded != null) {
                    excluded.add(acc.excluded);
                    continue;
                }
                kcal = kcal.add(acc.kcal);
                protein = protein.add(acc.protein);
                fat = fat.add(acc.fat);
                carb = carb.add(acc.carb);
            }
        }
        Macros total = round(new Macros(kcal, protein, fat, carb));
        return new NutritionEstimate(true, false, servings, total, perServing(total, servings), List.copyOf(excluded));
    }

    private Acc accumulate(String name, BigDecimal amount, String unit) {
        String label = name == null ? "未命名" : name.trim();
        if (amount == null) {
            return Acc.skip("适量的" + label + "（无重量，未计入）");
        }
        Optional<FoodEntry> found = table.find(label);
        if (found.isEmpty()) {
            return Acc.skip(label + "（营养表里没有，未计入）");
        }
        FoodEntry food = found.get();
        Optional<BigDecimal> grams = Units.toGrams(amount, unit, food.pieceGrams(), label);
        if (grams.isEmpty()) {
            String u = unit == null || unit.isBlank() ? "没有单位" : "单位「" + unit + "」无法换算";
            return Acc.skip(label + "（" + u + "，未计入）");
        }
        if (food.zeroEnergy()) {
            return Acc.zero();
        }
        return new Acc(
                Units.scalePer100g(food.kcal(), grams.get()),
                Units.scalePer100g(food.protein(), grams.get()),
                Units.scalePer100g(food.fat(), grams.get()),
                Units.scalePer100g(food.carb(), grams.get()),
                null);
    }

    private static Macros macros(BigDecimal kcal, BigDecimal protein, BigDecimal fat, BigDecimal carb) {
        return new Macros(nz(kcal), nz(protein), nz(fat), nz(carb));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Macros scale(Macros base, int servings, int def) {
        if (def <= 0 || servings == def) return round(base);
        BigDecimal f = BigDecimal.valueOf(servings).divide(BigDecimal.valueOf(def), 6, RoundingMode.HALF_UP);
        return round(new Macros(base.kcal().multiply(f), base.protein().multiply(f),
                base.fat().multiply(f), base.carb().multiply(f)));
    }

    private static Macros perServing(Macros total, int servings) {
        int s = Math.max(1, servings);
        return round(new Macros(
                total.kcal().divide(BigDecimal.valueOf(s), 4, RoundingMode.HALF_UP),
                total.protein().divide(BigDecimal.valueOf(s), 4, RoundingMode.HALF_UP),
                total.fat().divide(BigDecimal.valueOf(s), 4, RoundingMode.HALF_UP),
                total.carb().divide(BigDecimal.valueOf(s), 4, RoundingMode.HALF_UP)));
    }

    private static Macros round(Macros m) {
        return new Macros(Amounts.normalize(m.kcal()), Amounts.normalize(m.protein()),
                Amounts.normalize(m.fat()), Amounts.normalize(m.carb()));
    }

    private record Acc(BigDecimal kcal, BigDecimal protein, BigDecimal fat, BigDecimal carb, String excluded) {
        static Acc skip(String reason) {
            return new Acc(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, reason);
        }
        static Acc zero() {
            return new Acc(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
    }
}
