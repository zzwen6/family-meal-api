package com.zzwen6.familymeal.stats;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;

import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {
    private StatsDtos() {}

    public record DishBrief(Long dishId, String name, int servings) {}

    public record DayMeal(LocalDate date, MealType mealType, Long menuId, MenuStatus status, boolean plannedOnly,
                          List<DishBrief> items,
                          Long assignedMemberId, String assignedMemberName,
                          Long confirmedById, String confirmedByName,
                          Double averageScore, int ratingCount) {}

    public record DishCookStat(Long dishId, String name, int times, LocalDate lastCooked, Double averageScore) {}

    public record DishWishStat(Long dishId, String name, int wantCount, int selectedCount, double selectedRate) {}

    public record MemberFairness(Long memberId, String displayName, int wantCount, int fulfilledCount,
                                 double fulfillmentRate, int assignedCookCount, int confirmedCookCount) {}

    public record FatigueStat(Long dishId, String name, int times, int windowDays, int threshold) {}

    public record StatsView(LocalDate from, LocalDate to,
                            List<DayMeal> days,
                            List<DishCookStat> frequentDishes,
                            List<DishWishStat> frequentWishes,
                            List<MemberFairness> fairness,
                            List<FatigueStat> fatigue) {}
}
