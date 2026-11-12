package com.zzwen6.familymeal.stats;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.Intensity;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.common.Names;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishRepository;
import com.zzwen6.familymeal.family.Family;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.family.FamilyMemberRepository;
import com.zzwen6.familymeal.family.FamilyRepository;
import com.zzwen6.familymeal.hint.MealHintService;
import com.zzwen6.familymeal.hint.MealHintService.DishRef;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.menu.MenuSlots;
import com.zzwen6.familymeal.rating.MealRating;
import com.zzwen6.familymeal.rating.MealRatingRepository;
import com.zzwen6.familymeal.rotation.CookAssignment;
import com.zzwen6.familymeal.rotation.CookAssignmentRepository;
import com.zzwen6.familymeal.stats.StatsDtos.*;
import com.zzwen6.familymeal.template.MealPlanItemRepository;
import com.zzwen6.familymeal.template.TemplateDtos.PlanItemView;
import com.zzwen6.familymeal.template.WeekTemplateService;
import com.zzwen6.familymeal.wish.MealWish;
import com.zzwen6.familymeal.wish.MealWishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 家庭日常统计：只看已确认 / 已开饭的本家庭菜单和点餐意愿，不含任何聚餐数据。
 */
@Service
public class StatsService {
    private final FamilyAccess access;
    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final MenuRepository menus;
    private final MealWishRepository wishes;
    private final MealRatingRepository ratings;
    private final CookAssignmentRepository assignments;
    private final MealHintService hints;
    private final DishRepository dishes;
    private final MenuSlots slots;
    private final WeekTemplateService templates;
    private final MealPlanItemRepository plans;

    public StatsService(FamilyAccess access, FamilyRepository families, FamilyMemberRepository members,
                        MenuRepository menus, MealWishRepository wishes, MealRatingRepository ratings,
                        CookAssignmentRepository assignments, MealHintService hints, DishRepository dishes,
                        MenuSlots slots, WeekTemplateService templates, MealPlanItemRepository plans) {
        this.access = access;
        this.families = families;
        this.members = members;
        this.menus = menus;
        this.wishes = wishes;
        this.ratings = ratings;
        this.assignments = assignments;
        this.hints = hints;
        this.dishes = dishes;
        this.slots = slots;
        this.templates = templates;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public StatsView stats(Long familyId, LocalDate from, LocalDate to) {
        access.member(familyId);
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        LocalDate today = slots.today();
        if (from == null && to == null) {
            from = today.with(DayOfWeek.MONDAY);
            to = from.plusDays(6);
        } else if (from == null) {
            from = to.minusDays(29);
        } else if (to == null) {
            to = from.plusDays(29);
        }
        if (to.isBefore(from) || to.isAfter(from.plusDays(92))) {
            throw BizException.badRequest("日期范围不正确（最多 93 天）");
        }

        List<FamilyMember> all = members.findByFamilyIdOrderByIdAsc(familyId);
        Map<Long, String> names = all.stream().collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));

        List<Menu> rangeMenus = menus.findWithItems(familyId, from, to);
        Map<String, Menu> menuBySlot = rangeMenus.stream()
                .collect(Collectors.toMap(m -> slotKey(m.getMealDate(), m.getMealType()), Function.identity(), (a, b) -> a));
        List<Menu> sealed = rangeMenus.stream().filter(StatsService::isSealed).toList();

        Map<Long, List<MealRating>> ratingsByMenu = ratings.findByFamilyIdAndMenuIdIn(familyId,
                        sealed.stream().map(Menu::getId).toList())
                .stream().collect(Collectors.groupingBy(MealRating::getMenuId));

        Map<String, CookAssignment> assignBySlot = assignments
                .findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(familyId, from, to)
                .stream().collect(Collectors.toMap(a -> slotKey(a.getMealDate(), a.getMealType()), Function.identity(), (a, b) -> a));

        List<PlanItemView> planViews = templates.plans(familyId, from, to);
        Map<String, List<PlanItemView>> plansBySlot = planViews.stream()
                .collect(Collectors.groupingBy(p -> slotKey(p.mealDate(), p.mealType()), LinkedHashMap::new, Collectors.toList()));

        // ---- 每天吃了什么 / 计划 ----
        Set<String> dayKeys = new TreeSet<>();
        sealed.forEach(m -> dayKeys.add(slotKey(m.getMealDate(), m.getMealType())));
        plansBySlot.keySet().forEach(dayKeys::add);
        assignBySlot.keySet().forEach(dayKeys::add);

        List<DayMeal> days = new ArrayList<>();
        for (String key : dayKeys) {
            String[] parts = key.split("\\|", 2);
            LocalDate date = LocalDate.parse(parts[0]);
            var type = com.zzwen6.familymeal.common.Enums.MealType.valueOf(parts[1]);
            Menu menu = menuBySlot.get(key);
            CookAssignment asg = assignBySlot.get(key);
            Long assignedId = asg == null ? null : asg.getMemberId();
            String assignedName = assignedId == null ? null : names.getOrDefault(assignedId, "已退出成员");
            if (menu != null && isSealed(menu)) {
                List<DishBrief> items = hints.refsOf(familyId, menu.getItems()).stream()
                        .map(r -> new DishBrief(r.dishId(), r.name(), servingsOf(menu, r)))
                        .toList();
                List<MealRating> rs = ratingsByMenu.getOrDefault(menu.getId(), List.of());
                Double avg = rs.isEmpty() ? null : rs.stream().mapToInt(MealRating::getScore).average().orElse(0);
                days.add(new DayMeal(date, type, menu.getId(), menu.getStatus(), false, items,
                        assignedId, assignedName, menu.getConfirmedBy(),
                        menu.getConfirmedBy() == null ? null : names.getOrDefault(menu.getConfirmedBy(), "已退出成员"),
                        avg, rs.size()));
            } else {
                List<PlanItemView> ps = plansBySlot.getOrDefault(key, List.of());
                List<DishBrief> items = ps.stream().map(p -> new DishBrief(p.dishId(), p.name(), p.servings())).toList();
                days.add(new DayMeal(date, type, menu == null ? null : menu.getId(),
                        menu == null ? null : menu.getStatus(), true, items,
                        assignedId, assignedName, null, null, null, 0));
            }
        }

        // ---- 常做的菜 ----
        Map<String, Acc> cooked = new LinkedHashMap<>();
        for (Menu m : sealed) {
            List<MealRating> rs = ratingsByMenu.getOrDefault(m.getId(), List.of());
            for (DishRef ref : hints.refsOf(familyId, m.getItems())) {
                String k = dishKey(ref);
                if (k.isEmpty()) continue;
                Acc a = cooked.computeIfAbsent(k, x -> new Acc(ref.dishId(), ref.name()));
                if (a.dishId == null && ref.dishId() != null) a.dishId = ref.dishId();
                a.times++;
                if (a.last == null || m.getMealDate().isAfter(a.last)) a.last = m.getMealDate();
                rs.forEach(r -> a.scores.add(r.getScore()));
            }
        }
        List<DishCookStat> frequentDishes = cooked.values().stream()
                .sorted(Comparator.comparingInt((Acc a) -> a.times).reversed().thenComparing(a -> a.name))
                .map(a -> new DishCookStat(a.dishId, a.name, a.times, a.last,
                        a.scores.isEmpty() ? null : a.scores.stream().mapToInt(i -> i).average().orElse(0)))
                .toList();

        // ---- 常点的菜（WANT）与被选中比例 ----
        List<MealWish> rangeWishes = wishes.findByFamilyIdAndMealDateBetweenOrderByMealDateAscIdAsc(familyId, from, to);
        Map<String, Menu> sealedBySlot = sealed.stream()
                .collect(Collectors.toMap(m -> slotKey(m.getMealDate(), m.getMealType()), Function.identity(), (a, b) -> a));
        Map<String, Acc> wanted = new LinkedHashMap<>();
        for (MealWish w : rangeWishes) {
            if (w.isNotEatingHome() || w.getIntensity() != Intensity.WANT) continue;
            DishRef ref = wishRef(familyId, w);
            String k = dishKey(ref);
            if (k.isEmpty()) continue;
            Acc a = wanted.computeIfAbsent(k, x -> new Acc(ref.dishId(), ref.name()));
            if (a.dishId == null && ref.dishId() != null) a.dishId = ref.dishId();
            a.times++;
            Menu menu = sealedBySlot.get(slotKey(w.getMealDate(), w.getMealType()));
            if (menu != null && hints.fulfilled(familyId, menu, w)) a.selected++;
        }
        List<DishWishStat> frequentWishes = wanted.values().stream()
                .sorted(Comparator.comparingInt((Acc a) -> a.times).reversed())
                .map(a -> new DishWishStat(a.dishId, a.name, a.times, a.selected,
                        a.times == 0 ? 0 : (double) a.selected / a.times))
                .toList();

        // ---- 心愿公平 + 掌厨次数 ----
        Map<Long, int[]> fair = new LinkedHashMap<>(); // [fulfilled, wants]
        for (FamilyMember m : all) fair.put(m.getId(), new int[]{0, 0});
        for (MealWish w : rangeWishes) {
            if (w.isNotEatingHome() || w.getIntensity() != Intensity.WANT) continue;
            int[] t = fair.computeIfAbsent(w.getMemberId(), k -> new int[]{0, 0});
            t[1]++;
            Menu menu = sealedBySlot.get(slotKey(w.getMealDate(), w.getMealType()));
            if (menu != null && hints.fulfilled(familyId, menu, w)) t[0]++;
        }
        Map<Long, int[]> cookTally = new LinkedHashMap<>(); // [assigned, confirmed]
        for (FamilyMember m : all) cookTally.put(m.getId(), new int[]{0, 0});
        for (CookAssignment a : assignBySlot.values()) {
            cookTally.computeIfAbsent(a.getMemberId(), k -> new int[]{0, 0})[0]++;
        }
        for (Menu m : sealed) {
            if (m.getConfirmedBy() != null) {
                cookTally.computeIfAbsent(m.getConfirmedBy(), k -> new int[]{0, 0})[1]++;
            }
        }
        List<MemberFairness> fairness = all.stream().map(m -> {
            int[] t = fair.getOrDefault(m.getId(), new int[]{0, 0});
            int[] c = cookTally.getOrDefault(m.getId(), new int[]{0, 0});
            double rate = t[1] == 0 ? 0 : (double) t[0] / t[1];
            return new MemberFairness(m.getId(), m.getDisplayName(), t[1], t[0], rate, c[0], c[1]);
        }).toList();

        // ---- 吃腻：复用家庭窗口与阈值，在统计区间内（与窗口相交）计数 ----
        int window = family.getFatigueWindowDays();
        int threshold = family.getFatigueThreshold();
        LocalDate fatigueFrom = to.minusDays(window - 1L);
        if (fatigueFrom.isBefore(from)) fatigueFrom = from;
        Map<String, Acc> fat = new LinkedHashMap<>();
        for (Menu m : menus.findWithItems(familyId, fatigueFrom, to)) {
            if (!isSealed(m)) continue;
            for (DishRef ref : hints.refsOf(familyId, m.getItems())) {
                String k = dishKey(ref);
                if (k.isEmpty()) continue;
                Acc a = fat.computeIfAbsent(k, x -> new Acc(ref.dishId(), ref.name()));
                if (a.dishId == null && ref.dishId() != null) a.dishId = ref.dishId();
                a.times++;
            }
        }
        List<FatigueStat> fatigue = fat.values().stream()
                .filter(a -> a.times >= threshold)
                .sorted(Comparator.comparingInt((Acc a) -> a.times).reversed())
                .map(a -> new FatigueStat(a.dishId, a.name, a.times, window, threshold))
                .toList();

        return new StatsView(from, to, days, frequentDishes, frequentWishes, fairness, fatigue);
    }

    private int servingsOf(Menu menu, DishRef ref) {
        return menu.getItems().stream()
                .filter(i -> (ref.dishId() != null && ref.dishId().equals(i.getDishId()))
                        || (ref.dishId() == null && Names.same(ref.name(), i.getCustomName())))
                .mapToInt(i -> i.getServings())
                .findFirst().orElse(1);
    }

    private DishRef wishRef(Long familyId, MealWish w) {
        if (w.getDishId() != null) {
            String name = dishes.findByIdAndFamilyId(w.getDishId(), familyId).map(Dish::getName)
                    .orElse(w.getFreeText() == null ? "已删除菜品" : w.getFreeText());
            return new DishRef(w.getDishId(), name);
        }
        return new DishRef(null, w.getFreeText());
    }

    private static String dishKey(DishRef ref) {
        if (ref.dishId() != null) return "d" + ref.dishId();
        return "n" + Names.normalize(ref.name());
    }

    private static String slotKey(LocalDate date, Enum<?> type) {
        return date + "|" + type;
    }

    private static boolean isSealed(Menu m) {
        return m.getStatus() == MenuStatus.CONFIRMED || m.getStatus() == MenuStatus.READY;
    }

    private static final class Acc {
        Long dishId;
        final String name;
        int times;
        int selected;
        LocalDate last;
        final List<Integer> scores = new ArrayList<>();

        Acc(Long dishId, String name) {
            this.dishId = dishId;
            this.name = name == null ? "未命名" : name;
        }
    }
}
