package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.family.Family;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 餐次的查找/创建与截止时间计算，点餐和菜单两边共用。 */
@Component
public class MenuSlots {
    private final MenuRepository menus;
    private final Clock clock;

    public MenuSlots(MenuRepository menus, Clock clock) {
        this.menus = menus;
        this.clock = clock;
    }

    public Menu ensure(Long familyId, LocalDate date, MealType type) {
        return menus.findByFamilyIdAndMealDateAndMealType(familyId, date, type).orElseGet(() -> {
            Menu m = new Menu();
            m.setFamilyId(familyId);
            m.setMealDate(date);
            m.setMealType(type);
            m.setStatus(MenuStatus.OPEN);
            return menus.saveAndFlush(m);
        });
    }

    public LocalDateTime deadline(Family f, Menu menu, LocalDate date, MealType type) {
        if (menu != null && menu.getDeadlineOverride() != null) return menu.getDeadlineOverride();
        return f.deadlineFor(date, type);
    }

    /** 普通成员是否还能提交/修改意愿。 */
    public boolean openForMembers(Family f, Menu menu, LocalDate date, MealType type) {
        if (menu != null && menu.getStatus() != MenuStatus.OPEN) return false;
        return now().isBefore(deadline(f, menu, date, type));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
