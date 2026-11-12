package com.zzwen6.familymeal.rotation;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.Role;
import com.zzwen6.familymeal.family.FamilyMember;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 本餐谁可以进厨房：没有指定安排时，和现在一样（ADMIN / COOK）；
 * 有指定时，指定的人（不必改 Role）、管理员、以及其他掌厨（代做）都可以。
 */
@Component
public class KitchenDuty {
    private final CookAssignmentRepository assignments;

    public KitchenDuty(CookAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    public Optional<CookAssignment> assignment(Long familyId, LocalDate date, MealType type) {
        return assignments.findByFamilyIdAndMealDateAndMealType(familyId, date, type);
    }

    public boolean canRunKitchen(FamilyMember me, LocalDate date, MealType type) {
        if (me.getRole() == Role.CHILD) return false;
        if (me.getRole().canCook()) return true;
        return assignment(me.getFamilyId(), date, type)
                .map(a -> a.getMemberId().equals(me.getId()))
                .orElse(false);
    }

    public void requireKitchen(FamilyMember me, LocalDate date, MealType type) {
        if (!canRunKitchen(me, date, type)) {
            throw BizException.forbidden("只有本餐指定掌厨、管理员或其他掌厨可以这样做");
        }
    }

    /**
     * 改派 / 对调：管理员随时可以；该餐已被指定时，被指定的人可以改；
     * 还没有安排时，掌厨也可以指定。
     */
    public boolean canManageAssignment(FamilyMember me, LocalDate date, MealType type) {
        if (me.getRole() == Role.CHILD) return false;
        if (me.getRole().isAdmin()) return true;
        Optional<CookAssignment> a = assignment(me.getFamilyId(), date, type);
        if (a.isPresent()) return a.get().getMemberId().equals(me.getId());
        return me.getRole().canCook();
    }

    public void requireManage(FamilyMember me, LocalDate date, MealType type) {
        if (!canManageAssignment(me, date, type)) {
            throw BizException.forbidden("只有管理员或本餐指定掌厨可以改派");
        }
    }
}
