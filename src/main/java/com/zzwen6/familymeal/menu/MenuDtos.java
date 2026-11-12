package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuItemSource;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.common.Enums.Role;
import com.zzwen6.familymeal.hint.HintDtos.HintView;
import com.zzwen6.familymeal.inventory.InventoryDtos.LeftoverView;
import com.zzwen6.familymeal.rating.RatingDtos.RatingView;
import com.zzwen6.familymeal.shopping.ShoppingDtos.ShoppingListView;
import com.zzwen6.familymeal.template.TemplateDtos.PlanItemView;
import com.zzwen6.familymeal.wish.WishDtos.WishView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MenuDtos {
    private MenuDtos() {}

    /**
     * 确认菜单中的一道菜：dishId（菜谱里的菜）或 customName（临时菜名）二选一。
     * source：WISH 采纳家人意愿 / COOK_ADDED 掌厨加的 / SUBSTITUTE 替代某道被点的菜（填 substituteForDishId）。
     */
    public record ConfirmItem(Long dishId,
                              @Size(max = 64) String customName,
                              @Min(1) @Max(100) Integer servings,
                              MenuItemSource source,
                              Long substituteForDishId,
                              @Size(max = 255) String note) {}

    public record ConfirmRequest(@NotNull LocalDate mealDate,
                                 @NotNull MealType mealType,
                                 @NotEmpty(message = "至少确认一道菜") @Valid List<ConfirmItem> items,
                                 @Size(max = 500) String note,
                                 List<Long> useLeftoverIds) {}

    public record ReopenRequest(LocalDateTime deadline) {}

    /** 按餐次重新开放（餐次可能还没有人点过，没有 menuId）。 */
    public record ReopenSlotRequest(@NotNull LocalDate mealDate, @NotNull MealType mealType, LocalDateTime deadline) {}

    public record MenuItemView(Long id, Long dishId, String name, int servings, MenuItemSource source,
                               Long substituteForDishId, String substituteForName, String note,
                               String coverUrl, Integer durationMinutes, String category) {}

    public record MenuView(Long id, LocalDate mealDate, MealType mealType, MenuStatus status, LocalDateTime deadline,
                           String note, LocalDateTime confirmedAt, String confirmedByName, LocalDateTime readyAt,
                           List<MenuItemView> items, int wishCount,
                           Long assignedMemberId, String assignedMemberName, String assignedSource,
                           boolean proxyConfirm) {}

    public record MemberStatus(Long memberId, String displayName, Role role, boolean submitted,
                               boolean notEatingHome, String allergies, String dietaryNotes) {}

    /** 按菜汇总的意愿，掌厨据此决定做什么。 */
    public record DishVote(Long dishId, String name, int wantCount, int okCount, int avoidCount, int servings,
                           List<String> wantBy, List<String> avoidBy) {}

    public record CookBrief(Long memberId, String displayName, String source) {}

    /** 掌厨工作台/今晚概览：一屏看全员意愿、指定掌厨和提示。 */
    public record BoardView(LocalDate mealDate, MealType mealType, MenuView menu, boolean canEditWish,
                            boolean canCook, boolean canRunKitchen, boolean canManageAssignment,
                            LocalDateTime deadline, int headcount, List<MemberStatus> members,
                            List<DishVote> votes, List<WishView> wishes,
                            CookBrief assignedCook, boolean proxyConfirm,
                            List<HintView> hints, List<LeftoverView> leftovers,
                            List<PlanItemView> planItems, List<RatingView> ratings, RatingView myRating) {}

    public record ConfirmResponse(MenuView menu, ShoppingListView shoppingList, List<HintView> hints) {}

    public record HintPreview(List<HintView> hints) {}
}
