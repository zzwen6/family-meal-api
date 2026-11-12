package com.zzwen6.familymeal.shopping;

import com.zzwen6.familymeal.common.Enums.IngredientKind;
import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ShoppingDtos {
    private ShoppingDtos() {}

    public record ItemView(Long id, String name, BigDecimal amount, String unit, IngredientKind kind, boolean staple,
                           boolean toBuy, boolean purchased, String sourceDishes, String note) {
        public static ItemView of(ShoppingItem i) {
            return new ItemView(i.getId(), i.getName(), i.getAmount(), i.getUnit(), i.getKind(), i.isStaple(),
                    i.isToBuy(), i.isPurchased(), i.getSourceDishes(), i.getNote());
        }
    }

    /** buyItems：要买的；stapleItems：家里常备、默认不买（掌厨可勾回去）。 */
    public record ShoppingListView(Long id, Long menuId, LocalDate mealDate, MealType mealType,
                                   LocalDateTime generatedAt, List<ItemView> buyItems, List<ItemView> stapleItems,
                                   int toBuyCount, int purchasedCount) {}

    public record UpdateItemRequest(Boolean toBuy, Boolean purchased) {}

    public record AddItemRequest(@NotBlank(message = "名称不能为空") @Size(max = 64) String name,
                                 @DecimalMin("0") BigDecimal amount,
                                 @Size(max = 16) String unit) {}
}
