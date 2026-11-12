package com.zzwen6.familymeal.template;

import com.zzwen6.familymeal.common.Enums.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class TemplateDtos {
    private TemplateDtos() {}

    public record TemplateItemInput(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull MealType mealType,
            Long dishId,
            @Size(max = 64) String customName,
            @Min(1) @Max(100) Integer servings) {}

    public record TemplateRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 255) String note,
            @Valid List<TemplateItemInput> items) {}

    public record FromWeekRequest(
            @NotNull LocalDate weekStart,
            @Size(max = 64) String name,
            @Size(max = 255) String note) {}

    public record ApplyRequest(
            @NotNull LocalDate weekStart,
            Boolean overwrite) {}

    public record TemplateItemView(Long id, int dayOfWeek, MealType mealType, Long dishId, String name,
                                   int servings, String coverUrl) {}

    public record TemplateView(Long id, String name, String note, List<TemplateItemView> items) {}

    public record PlanItemView(Long id, LocalDate mealDate, MealType mealType, Long dishId, String name,
                               int servings, String coverUrl, Long sourceTemplateId) {}

    public record ApplyResult(LocalDate weekStart, boolean overwrite,
                              int appliedSlots, int skippedConfirmed, int skippedExistingPlan, int itemsWritten) {}
}
