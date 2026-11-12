package com.zzwen6.familymeal.hint;

import com.zzwen6.familymeal.common.Enums.Intensity;
import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.common.Names;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishRepository;
import com.zzwen6.familymeal.family.Family;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.family.FamilyMemberRepository;
import com.zzwen6.familymeal.family.FamilyRepository;
import com.zzwen6.familymeal.hint.HintDtos.HintView;
import com.zzwen6.familymeal.inventory.Leftover;
import com.zzwen6.familymeal.inventory.LeftoverRepository;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuItem;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.wish.MealWish;
import com.zzwen6.familymeal.wish.MealWishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 吃腻、心愿公平、忌口和剩菜提示。只统计本家庭已确认 / 已开饭的日常菜单，不含聚餐。
 */
@Service
public class MealHintService {
    private final FamilyAccess access;
    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final MenuRepository menus;
    private final MealWishRepository wishes;
    private final DishRepository dishes;
    private final LeftoverRepository leftovers;

    public MealHintService(FamilyAccess access, FamilyRepository families, FamilyMemberRepository members,
                           MenuRepository menus, MealWishRepository wishes, DishRepository dishes,
                           LeftoverRepository leftovers) {
        this.access = access;
        this.families = families;
        this.members = members;
        this.menus = menus;
        this.wishes = wishes;
        this.dishes = dishes;
        this.leftovers = leftovers;
    }

    @Transactional(readOnly = true)
    public List<HintView> list(Long familyId, LocalDate date, MealType type, Long dishId, String name) {
        access.member(familyId);
        Family family = families.findById(familyId).orElseThrow();
        List<HintView> hints = new ArrayList<>();
        if (dishId != null || (name != null && !name.isBlank())) {
            hints.addAll(fatigueFor(family, date, type, candidates(familyId, date, type, dishId, name)));
            hints.addAll(avoidFor(family, date, type, List.of(new DishRef(dishId, name))));
        } else {
            hints.addAll(allFatigue(family, date, type));
        }
        hints.addAll(fairness(family, date, type));
        hints.addAll(leftoverHints(familyId));
        return hints;
    }

    /** 今晚概况：窗口内所有已达阈值的吃腻提示、公平、已确认菜单上的忌口、可利用的剩菜。 */
    @Transactional(readOnly = true)
    public List<HintView> forBoard(Family family, LocalDate date, MealType type, List<DishRef> onBoard) {
        List<HintView> hints = new ArrayList<>();
        hints.addAll(allFatigue(family, date, type));
        hints.addAll(fairness(family, date, type));
        hints.addAll(avoidFor(family, date, type, confirmedOrNone(family.getId(), date, type, onBoard)));
        hints.addAll(leftoverHints(family.getId()));
        return hints;
    }

    /** 确认预览：针对即将写入菜单的菜给出吃腻 / 忌口 / 公平提示。 */
    @Transactional(readOnly = true)
    public List<HintView> forItems(Family family, LocalDate date, MealType type, List<DishRef> items) {
        List<HintView> hints = new ArrayList<>();
        hints.addAll(fatigueFor(family, date, type, items));
        hints.addAll(fairness(family, date, type));
        hints.addAll(avoidFor(family, date, type, items));
        // 菜单里点了对应剩菜时也提示会被用掉
        Set<String> names = items.stream().map(r -> Names.normalize(r.name())).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        for (Leftover l : leftovers.findByFamilyIdAndUsedFalseOrderByExpiresOnAscIdAsc(family.getId())) {
            if (names.contains(Names.normalize(l.getName()))) hints.add(HintView.leftover(l.getId(), l.getName(), l.getExpiresOn()));
        }
        return hints;
    }

    public record DishRef(Long dishId, String name) {}

    private List<DishRef> candidates(Long familyId, LocalDate date, MealType type, Long dishId, String name) {
        if (dishId != null || (name != null && !name.isBlank())) {
            String n = name;
            if (n == null && dishId != null) {
                n = dishes.findByIdAndFamilyId(dishId, familyId).map(Dish::getName).orElse("这道菜");
            }
            return List.of(new DishRef(dishId, n));
        }
        List<DishRef> refs = new ArrayList<>();
        Menu menu = menus.findByFamilyIdAndMealDateAndMealType(familyId, date, type).orElse(null);
        if (menu != null) refs.addAll(refsOf(familyId, menu.getItems()));
        for (MealWish w : wishes.findByFamilyIdAndMealDateAndMealTypeOrderByIdAsc(familyId, date, type)) {
            if (w.isNotEatingHome()) continue;
            refs.add(new DishRef(w.getDishId(), wishName(familyId, w)));
        }
        return refs;
    }

    private List<DishRef> confirmedOrNone(Long familyId, LocalDate date, MealType type, List<DishRef> onBoard) {
        Menu menu = menus.findByFamilyIdAndMealDateAndMealType(familyId, date, type).orElse(null);
        if (menu != null && (menu.getStatus() == MenuStatus.CONFIRMED || menu.getStatus() == MenuStatus.READY)) {
            return refsOf(familyId, menu.getItems());
        }
        return List.of(); // 未确认前，忌口只在确认预览里强调
    }

    public List<DishRef> refsOf(Long familyId, List<MenuItem> items) {
        Set<Long> ids = items.stream().map(MenuItem::getDishId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Dish> dishMap = ids.isEmpty() ? Map.of() : dishes.findByFamilyIdAndIdIn(familyId, ids).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        List<DishRef> refs = new ArrayList<>();
        for (MenuItem i : items) {
            String name = i.getDishId() != null && dishMap.containsKey(i.getDishId())
                    ? dishMap.get(i.getDishId()).getName()
                    : (i.getCustomName() == null ? "未命名" : i.getCustomName());
            refs.add(new DishRef(i.getDishId(), name));
        }
        return refs;
    }

    private List<HintView> allFatigue(Family family, LocalDate date, MealType type) {
        Counts c = counts(family, date, type);
        List<HintView> hints = new ArrayList<>();
        for (var e : c.counts.entrySet()) {
            if (e.getValue() >= c.threshold) {
                hints.add(HintView.fatigue(c.dishIds.get(e.getKey()), c.display.getOrDefault(e.getKey(), "这道菜"),
                        e.getValue(), c.window));
            }
        }
        return hints;
    }

    private List<HintView> fatigueFor(Family family, LocalDate date, MealType type, List<DishRef> targets) {
        if (targets == null || targets.isEmpty()) return List.of();
        Counts c = counts(family, date, type);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<HintView> hints = new ArrayList<>();
        for (DishRef ref : targets) {
            String key = dishKey(ref);
            if (key.isEmpty() || !seen.add(key)) continue;
            int n = c.counts.getOrDefault(key, 0);
            if (n >= c.threshold) {
                hints.add(HintView.fatigue(ref.dishId() != null ? ref.dishId() : c.dishIds.get(key),
                        ref.name() != null ? ref.name() : c.display.getOrDefault(key, "这道菜"), n, c.window));
            }
        }
        return hints;
    }

    private Counts counts(Family family, LocalDate date, MealType type) {
        Counts c = new Counts();
        c.window = family.getFatigueWindowDays();
        c.threshold = family.getFatigueThreshold();
        LocalDate from = date.minusDays(c.window - 1L);
        for (Menu m : menus.findWithItems(family.getId(), from, date)) {
            if (!isSealed(m)) continue;
            if (m.getMealDate().equals(date) && m.getMealType() == type) continue; // 不含本餐
            for (DishRef ref : refsOf(family.getId(), m.getItems())) {
                String key = dishKey(ref);
                if (key.isEmpty()) continue;
                c.counts.merge(key, 1, Integer::sum);
                c.display.putIfAbsent(key, ref.name());
                if (ref.dishId() != null) c.dishIds.putIfAbsent(key, ref.dishId());
            }
        }
        return c;
    }

    private static final class Counts {
        int window;
        int threshold;
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, Long> dishIds = new HashMap<>();
        final Map<String, String> display = new HashMap<>();
    }

    private List<HintView> fairness(Family family, LocalDate date, MealType type) {
        int window = family.getFatigueWindowDays();
        LocalDate from = date.minusDays(window - 1L);
        List<FamilyMember> all = members.findByFamilyIdOrderByIdAsc(family.getId());
        Map<Long, String> names = all.stream().collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));
        Map<String, Menu> sealed = menus.findWithItems(family.getId(), from, date)
                .stream().filter(MealHintService::isSealed)
                .collect(Collectors.toMap(m -> m.getMealDate() + "|" + m.getMealType(), Function.identity(), (a, b) -> a));
        Map<Long, int[]> tally = new LinkedHashMap<>(); // [fulfilled, wants]
        for (MealWish w : wishes.findByFamilyIdAndMealDateBetweenOrderByMealDateAscIdAsc(family.getId(), from, date)) {
            if (w.isNotEatingHome() || w.getIntensity() != Intensity.WANT) continue;
            int[] t = tally.computeIfAbsent(w.getMemberId(), k -> new int[2]);
            t[1]++;
            Menu menu = sealed.get(w.getMealDate() + "|" + w.getMealType());
            if (menu != null && matchedIn(family.getId(), menu, w)) t[0]++;
        }
        List<int[]> active = tally.values().stream().filter(t -> t[1] > 0).toList();
        if (active.isEmpty()) return List.of();
        double avg = active.stream().mapToDouble(t -> (double) t[0] / t[1]).average().orElse(1);
        boolean allEqual = active.stream().mapToDouble(t -> (double) t[0] / t[1]).distinct().count() == 1;
        List<HintView> hints = new ArrayList<>();
        for (var e : tally.entrySet()) {
            int fulfilled = e.getValue()[0], wants = e.getValue()[1];
            if (wants == 0) continue;
            double ratio = (double) fulfilled / wants;
            if (fulfilled == 0 || (!allEqual && ratio < avg - 1e-9)) {
                String name = names.getOrDefault(e.getKey(), "已退出成员");
                hints.add(HintView.fairness(e.getKey(), name, fulfilled, wants, window));
            }
        }
        return hints;
    }

    private List<HintView> avoidFor(Family family, LocalDate date, MealType type, List<DishRef> items) {
        if (items == null || items.isEmpty()) return List.of();
        List<MealWish> list = wishes.findByFamilyIdAndMealDateAndMealTypeOrderByIdAsc(family.getId(), date, type);
        Map<Long, String> names = members.findByFamilyIdOrderByIdAsc(family.getId()).stream()
                .collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));
        List<HintView> hints = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DishRef item : items) {
            for (MealWish w : list) {
                if (w.getIntensity() != Intensity.AVOID || w.isNotEatingHome()) continue;
                if (!matchesWish(family.getId(), item, w)) continue;
                String key = item.dishId() + "|" + Names.normalize(item.name()) + "|" + w.getMemberId();
                if (!seen.add(key)) continue;
                hints.add(HintView.avoid(item.dishId(), item.name(), w.getMemberId(),
                        names.getOrDefault(w.getMemberId(), "已退出成员")));
            }
        }
        return hints;
    }

    private List<HintView> leftoverHints(Long familyId) {
        return leftovers.findByFamilyIdAndUsedFalseOrderByExpiresOnAscIdAsc(familyId).stream()
                .map(l -> HintView.leftover(l.getId(), l.getName(), l.getExpiresOn()))
                .toList();
    }

    /** 一条 WANT 意愿是否被这餐已确认菜单兑现（按菜谱 id 或规范化菜名，含替代菜）。 */
    public boolean fulfilled(Long familyId, Menu menu, MealWish w) {
        return matchedIn(familyId, menu, w);
    }

    private boolean matchedIn(Long familyId, Menu menu, MealWish w) {
        for (DishRef ref : refsOf(familyId, menu.getItems())) {
            if (matchesWish(familyId, ref, w)) return true;
        }
        return false;
    }

    private boolean matchesWish(Long familyId, DishRef ref, MealWish w) {
        if (ref.dishId() != null && w.getDishId() != null && ref.dishId().equals(w.getDishId())) return true;
        String wishName = wishName(familyId, w);
        return !Names.normalize(ref.name()).isEmpty() && Names.same(ref.name(), wishName);
    }

    private String wishName(Long familyId, MealWish w) {
        if (w.getFreeText() != null && !w.getFreeText().isBlank()) return w.getFreeText();
        if (w.getDishId() != null) {
            return dishes.findByIdAndFamilyId(w.getDishId(), familyId).map(Dish::getName).orElse("");
        }
        return "";
    }

    private static String dishKey(DishRef ref) {
        if (ref.dishId() != null) return "d" + ref.dishId();
        return "n" + Names.normalize(ref.name());
    }

    private static boolean isSealed(Menu m) {
        return m.getStatus() == MenuStatus.CONFIRMED || m.getStatus() == MenuStatus.READY;
    }
}
