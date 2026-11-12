package com.zzwen6.familymeal.wish;

import com.zzwen6.familymeal.common.Enums.Intensity;
import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class WishDtos {
    private WishDtos() {}

    /**
     * 提交点餐意愿。dishId 和 freeText 二选一（都填以 dishId 为准）；notEatingHome=true 表示这餐不在家吃。
     * memberId 不填表示本人；掌厨/管理员可以替孩子等成员填写。
     */
    public record WishRequest(
            @NotNull(message = "请选择日期") LocalDate mealDate,
            @NotNull(message = "请选择餐次") MealType mealType,
            Long dishId,
            @Size(max = 64, message = "想吃的菜最多 64 个字") String freeText,
            Intensity intensity,
            @Min(value = 1, message = "份数至少 1") @Max(value = 20, message = "份数最多 20") Integer servings,
            boolean notEatingHome,
            @Size(max = 255) String note,
            Long memberId) {}

    public record WishView(Long id, Long memberId, String memberName, LocalDate mealDate, MealType mealType,
                           Long dishId, String dishName, String freeText, Intensity intensity, int servings,
                           boolean notEatingHome, String note, boolean editable, LocalDateTime updatedAt) {}
}
