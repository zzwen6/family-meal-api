package com.zzwen6.familymeal.rotation;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.family.FamilyMember;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public final class CookRotationDtos {
    private CookRotationDtos() {}

    public record RotationMemberView(Long memberId, String displayName, int sortOrder) {
        public static RotationMemberView of(CookRotationMember m, FamilyMember fm) {
            return new RotationMemberView(m.getMemberId(),
                    fm == null ? "已退出成员" : fm.getDisplayName(), m.getSortOrder());
        }
    }

    public record RotationView(boolean enabled, List<MealType> mealTypes, List<RotationMemberView> members) {}

    public record UpdateRotationRequest(Boolean enabled, List<MealType> mealTypes, List<Long> memberIds) {}

    public record AssignmentView(LocalDate mealDate, MealType mealType, Long memberId, String memberName,
                                 CookAssignment.Source source, Long confirmedByMemberId, String confirmedByName,
                                 boolean proxyConfirm) {}

    public record AssignRequest(@NotNull LocalDate mealDate, @NotNull MealType mealType, Long memberId) {}

    public record GenerateRequest(LocalDate from, LocalDate to, Integer days) {}

    public record SwapRequest(@NotNull LocalDate date1, @NotNull MealType mealType1,
                              @NotNull LocalDate date2, @NotNull MealType mealType2) {}
}
