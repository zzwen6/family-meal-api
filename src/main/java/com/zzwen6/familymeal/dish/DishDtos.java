package com.zzwen6.familymeal.dish;

import com.zzwen6.familymeal.common.Enums.Difficulty;
import com.zzwen6.familymeal.common.Enums.IngredientKind;
import com.zzwen6.familymeal.nutrition.NutritionDtos.NutritionEstimate;
import com.zzwen6.familymeal.nutrition.NutritionDtos.NutritionOverride;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DishDtos {
    private DishDtos() {}

    public record StepInput(@NotBlank(message = "步骤内容不能为空") @Size(max = 1000) String content,
                            @Min(0) @Max(1440) Integer durationMinutes,
                            @Size(max = 512) String imageUrl,
                            @Size(max = 512) String videoUrl,
                            @Size(max = 512) String videoCoverUrl,
                            @Min(0) @Max(86400) Integer videoDurationSeconds) {}

    public record IngredientInput(IngredientKind kind,
                                  @NotBlank(message = "食材名称不能为空") @Size(max = 64) String name,
                                  @DecimalMin(value = "0", message = "用量不能为负") BigDecimal amount,
                                  @Size(max = 16) String unit,
                                  boolean staple,
                                  @Size(max = 255) String note) {}

    public record DishRequest(
            @NotBlank(message = "菜名不能为空") @Size(max = 64) String name,
            @NotBlank(message = "分类不能为空") @Size(max = 32) String category,
            Difficulty difficulty,
            @Min(value = 1, message = "用时至少 1 分钟") @Max(1440) int durationMinutes,
            @Min(value = 1, message = "默认份数至少 1") @Max(100) int defaultServings,
            @Size(max = 512) String coverUrl,
            @Size(max = 500) String description,
            @Size(max = 1000) String tips,
            @Valid List<StepInput> steps,
            /** 主料 */
            @Valid List<IngredientInput> ingredients,
            /** 配料与调味，和主料分开填 */
            @Valid List<IngredientInput> seasonings,
            /** 手工覆盖整道菜（按默认份数）的营养估算；enabled=true 时以手工值为准 */
            NutritionOverride nutrition) {}

    public record DishSummary(Long id, String name, String category, Difficulty difficulty, int durationMinutes,
                              int defaultServings, String coverUrl, String description, boolean active) {
        public static DishSummary of(Dish d) {
            return new DishSummary(d.getId(), d.getName(), d.getCategory(), d.getDifficulty(), d.getDurationMinutes(),
                    d.getDefaultServings(), d.getCoverUrl(), d.getDescription(), d.isActive());
        }
    }

    public record StepView(int stepNo, String content, Integer durationMinutes,
                           String imageUrl, String videoUrl, String videoCoverUrl, Integer videoDurationSeconds) {}

    public record IngredientView(Long id, IngredientKind kind, String name, BigDecimal amount, String unit,
                                 boolean staple, String note) {}

    /** 详情：servings 为换算后的份数，ingredients/seasonings 的 amount 已按份数缩放。 */
    public record DishDetail(Long id, String name, String category, Difficulty difficulty, int durationMinutes,
                             int defaultServings, int servings, String coverUrl, String description, String tips,
                             boolean active, List<StepView> steps, List<IngredientView> ingredients,
                             List<IngredientView> seasonings, NutritionEstimate nutrition,
                             boolean nutritionOverride, LocalDateTime updatedAt) {}
}
