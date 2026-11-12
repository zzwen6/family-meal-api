package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.family.Family;
import com.zzwen6.familymeal.family.FamilyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 每分钟把已过截止时间的 OPEN 餐次标记为 LOCKED（成员端即不可再改）。 */
@Component
public class MenuDeadlineJob {
    private static final Logger log = LoggerFactory.getLogger(MenuDeadlineJob.class);
    private final MenuRepository menus;
    private final FamilyRepository families;
    private final MenuSlots slots;

    public MenuDeadlineJob(MenuRepository menus, FamilyRepository families, MenuSlots slots) {
        this.menus = menus;
        this.families = families;
        this.slots = slots;
    }

    @Scheduled(cron = "${app.jobs.deadline-cron:0 * * * * *}", zone = "${app.timezone}")
    @Transactional
    public int closeExpired() {
        LocalDate today = slots.today();
        LocalDateTime now = slots.now();
        Map<Long, Family> cache = new HashMap<>();
        int closed = 0;
        // 早餐截止在前一天，所以多看一天；更早的 OPEN 餐次也顺带关掉
        for (Menu m : menus.findByStatusAndMealDateBetween(MenuStatus.OPEN, today.minusDays(30), today.plusDays(1))) {
            Family f = cache.computeIfAbsent(m.getFamilyId(), id -> families.findById(id).orElse(null));
            if (f == null) continue;
            if (!now.isBefore(slots.deadline(f, m, m.getMealDate(), m.getMealType()))) {
                m.setStatus(MenuStatus.LOCKED);
                closed++;
            }
        }
        if (closed > 0) log.info("已截止 {} 个餐次的点餐", closed);
        return closed;
    }
}
