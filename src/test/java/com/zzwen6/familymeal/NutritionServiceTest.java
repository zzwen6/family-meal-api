package com.zzwen6.familymeal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzwen6.familymeal.common.Enums.IngredientKind;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishIngredient;
import com.zzwen6.familymeal.nutrition.NutritionService;
import com.zzwen6.familymeal.nutrition.NutritionTable;
import com.zzwen6.familymeal.nutrition.NutritionDtos.NutritionEstimate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionServiceTest {
    static NutritionService service;

    @BeforeAll
    static void init() {
        service = new NutritionService(new NutritionTable(new ObjectMapper()));
    }

    @Test
    void knownIngredientConvertsByGramsAndAlias() {
        Dish d = dish(2, ing("鸡胸", "200", "g")); // 别名 → 鸡胸肉 133 kcal/100g
        NutritionEstimate e = service.ofDish(d, 2);
        assertThat(e.override()).isFalse();
        assertThat(e.estimated()).isTrue();
        // 200g × 1.33 = 266
        assertThat(e.total().kcal()).isEqualByComparingTo("266");
        assertThat(e.perServing().kcal()).isEqualByComparingTo("133");
        assertThat(e.excluded()).isEmpty();
    }

    @Test
    void unknownIngredientIsSkipped() {
        Dish d = dish(2, ing("月球岩石", "100", "g"), ing("鸡胸肉", "100", "克"));
        NutritionEstimate e = service.ofDish(d, 2);
        assertThat(e.total().kcal()).isEqualByComparingTo("133");
        assertThat(e.excluded()).anyMatch(s -> s.contains("月球岩石") && s.contains("未计入"));
    }

    @Test
    void scalesWithServings() {
        Dish d = dish(2, ing("米饭", "200", "g")); // 116 kcal/100g × 200g = 232 for 2 servings
        NutritionEstimate two = service.ofDish(d, 2);
        NutritionEstimate four = service.ofDish(d, 4);
        assertThat(two.total().kcal()).isEqualByComparingTo("232");
        assertThat(four.total().kcal()).isEqualByComparingTo("464");
        assertThat(four.perServing().kcal()).isEqualByComparingTo(two.perServing().kcal());
    }

    @Test
    void manualOverrideTakesPriority() {
        Dish d = dish(2, ing("鸡胸肉", "200", "g"));
        d.setNutritionOverride(true);
        d.setNutritionKcal(new BigDecimal("100"));
        d.setNutritionProtein(new BigDecimal("10"));
        d.setNutritionFat(new BigDecimal("2"));
        d.setNutritionCarb(new BigDecimal("5"));
        NutritionEstimate e = service.ofDish(d, 4); // 默认 2 人份 100 kcal → 4 人份 200
        assertThat(e.override()).isTrue();
        assertThat(e.estimated()).isFalse();
        assertThat(e.total().kcal()).isEqualByComparingTo("200");
        assertThat(e.perServing().kcal()).isEqualByComparingTo("50");
        assertThat(e.excluded()).isEmpty();
    }

    @Test
    void skipsUnknownAmountAndUnconvertibleUnit() {
        Dish d = dish(2, ing("盐", null, "g"), ing("香菜", "1", "把"));
        NutritionEstimate e = service.ofDish(d, 2);
        assertThat(e.total().kcal()).isEqualByComparingTo("0");
        assertThat(e.excluded()).anyMatch(s -> s.contains("适量的盐"));
        assertThat(e.excluded()).anyMatch(s -> s.contains("香菜") && s.contains("无法换算"));
    }

    @Test
    void saltWithWeightDoesNotAddCalories() {
        Dish d = dish(2, ing("盐", "10", "g"), ing("鸡胸肉", "100", "g"));
        NutritionEstimate e = service.ofDish(d, 2);
        assertThat(e.total().kcal()).isEqualByComparingTo("133");
        assertThat(e.excluded()).noneMatch(s -> s.contains("盐"));
    }

    private static Dish dish(int servings, DishIngredient... ings) {
        Dish d = new Dish();
        d.setName("测试菜");
        d.setDefaultServings(servings);
        d.getIngredients().addAll(List.of(ings));
        return d;
    }

    private static DishIngredient ing(String name, String amount, String unit) {
        DishIngredient i = new DishIngredient();
        i.setKind(IngredientKind.MAIN);
        i.setName(name);
        i.setAmount(amount == null ? null : new BigDecimal(amount));
        i.setUnit(unit);
        return i;
    }
}
