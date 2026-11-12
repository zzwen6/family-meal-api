package com.zzwen6.familymeal.wish;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.Intensity;
import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishRepository;
import com.zzwen6.familymeal.family.*;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.menu.MenuSlots;
import com.zzwen6.familymeal.wish.WishDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WishService {
    private final MealWishRepository wishes;
    private final FamilyAccess access;
    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final DishRepository dishes;
    private final MenuRepository menus;
    private final MenuSlots slots;

    public WishService(MealWishRepository wishes, FamilyAccess access, FamilyRepository families,
                       FamilyMemberRepository members, DishRepository dishes, MenuRepository menus, MenuSlots slots) {
        this.wishes = wishes;
        this.access = access;
        this.families = families;
        this.members = members;
        this.dishes = dishes;
        this.menus = menus;
        this.slots = slots;
    }

    @Transactional(readOnly = true)
    public List<WishView> list(Long familyId, LocalDate date, MealType type) {
        FamilyMember me = access.member(familyId);
        return toViews(familyId, wishes.findByFamilyIdAndMealDateAndMealTypeOrderByIdAsc(familyId, date, type), me);
    }

    @Transactional(readOnly = true)
    public List<WishView> mine(Long familyId, LocalDate from, LocalDate to) {
        FamilyMember me = access.member(familyId);
        LocalDate f = from == null ? slots.today() : from;
        LocalDate t = to == null ? f.plusDays(7) : to;
        if (t.isBefore(f) || t.isAfter(f.plusDays(62))) throw BizException.badRequest("日期范围不正确");
        return toViews(familyId, wishes.findByFamilyIdAndMemberIdAndMealDateBetweenOrderByMealDateAscIdAsc(
                familyId, me.getId(), f, t), me);
    }

    @Transactional
    public WishView create(Long familyId, WishRequest req) {
        FamilyMember me = access.member(familyId);
        FamilyMember target = resolveTarget(familyId, me, req.memberId());
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        Menu menu = slots.ensure(familyId, req.mealDate(), req.mealType());
        checkWritable(family, menu, me, req.mealDate(), req.mealType());

        List<MealWish> existing = wishes.findByFamilyIdAndMemberIdAndMealDateAndMealType(
                familyId, target.getId(), req.mealDate(), req.mealType());
        MealWish w;
        if (req.notEatingHome()) {
            // 不在家吃：清掉这一餐自己的其他意愿，只留一条标记
            existing.stream().filter(e -> !e.isNotEatingHome()).forEach(wishes::delete);
            w = existing.stream().filter(MealWish::isNotEatingHome).findFirst().orElseGet(MealWish::new);
        } else {
            existing.stream().filter(MealWish::isNotEatingHome).forEach(wishes::delete);
            Long dishId = req.dishId();
            String text = normalizeText(req.freeText());
            // 同一道菜重复提交视为修改
            w = existing.stream().filter(e -> !e.isNotEatingHome()
                            && (dishId != null ? dishId.equals(e.getDishId()) : e.getDishId() == null && Objects.equals(text, e.getFreeText())))
                    .findFirst().orElseGet(MealWish::new);
        }
        w.setFamilyId(familyId);
        w.setMemberId(target.getId());
        w.setMealDate(req.mealDate());
        w.setMealType(req.mealType());
        apply(familyId, w, req);
        wishes.save(w);
        return toViews(familyId, List.of(w), me).getFirst();
    }

    @Transactional
    public WishView update(Long familyId, Long wishId, WishRequest req) {
        FamilyMember me = access.member(familyId);
        MealWish w = wishes.findByIdAndFamilyId(wishId, familyId).orElseThrow(() -> BizException.notFound("点餐"));
        checkOwnerOrCook(me, w);
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        Menu oldMenu = menus.findByFamilyIdAndMealDateAndMealType(familyId, w.getMealDate(), w.getMealType()).orElse(null);
        checkWritable(family, oldMenu, me, w.getMealDate(), w.getMealType());
        if (!w.getMealDate().equals(req.mealDate()) || w.getMealType() != req.mealType()) {
            Menu newMenu = slots.ensure(familyId, req.mealDate(), req.mealType());
            checkWritable(family, newMenu, me, req.mealDate(), req.mealType());
            w.setMealDate(req.mealDate());
            w.setMealType(req.mealType());
        }
        apply(familyId, w, req);
        return toViews(familyId, List.of(w), me).getFirst();
    }

    @Transactional
    public void delete(Long familyId, Long wishId) {
        FamilyMember me = access.member(familyId);
        MealWish w = wishes.findByIdAndFamilyId(wishId, familyId).orElseThrow(() -> BizException.notFound("点餐"));
        checkOwnerOrCook(me, w);
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        Menu menu = menus.findByFamilyIdAndMealDateAndMealType(familyId, w.getMealDate(), w.getMealType()).orElse(null);
        checkWritable(family, menu, me, w.getMealDate(), w.getMealType());
        wishes.delete(w);
    }

    private void apply(Long familyId, MealWish w, WishRequest req) {
        if (req.notEatingHome()) {
            w.setNotEatingHome(true);
            w.setDishId(null);
            w.setFreeText(null);
            w.setIntensity(Intensity.OK);
            w.setServings(1);
        } else {
            String text = normalizeText(req.freeText());
            if (req.dishId() == null && text == null) throw BizException.badRequest("请选择菜品或写下想吃什么");
            if (req.dishId() != null) {
                Dish d = dishes.findByIdAndFamilyId(req.dishId(), familyId)
                        .orElseThrow(() -> BizException.notFound("菜品"));
                if (!d.isActive()) throw BizException.badRequest("这道菜已下架");
                w.setDishId(d.getId());
                w.setFreeText(null);
            } else {
                w.setDishId(null);
                w.setFreeText(text);
            }
            w.setNotEatingHome(false);
            w.setIntensity(req.intensity() == null ? Intensity.WANT : req.intensity());
            w.setServings(req.servings() == null ? 1 : req.servings());
        }
        w.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
    }

    private FamilyMember resolveTarget(Long familyId, FamilyMember me, Long memberId) {
        if (memberId == null || memberId.equals(me.getId())) return me;
        if (!me.getRole().canCook()) throw BizException.forbidden("只能替自己点餐");
        return members.findByIdAndFamilyId(memberId, familyId).orElseThrow(() -> BizException.notFound("成员"));
    }

    private void checkOwnerOrCook(FamilyMember me, MealWish w) {
        if (!w.getMemberId().equals(me.getId()) && !me.getRole().canCook()) {
            throw BizException.forbidden("只能修改自己的点餐");
        }
    }

    /**
     * 成员：餐次为 OPEN 且未过截止时间才能改。
     * 掌厨/管理员：截止后（LOCKED）仍可替家人补录；菜单一旦确认，所有人都要先「重新开放」才能改。
     */
    private void checkWritable(Family f, Menu menu, FamilyMember me, LocalDate date, MealType type) {
        if (date.isBefore(slots.today())) throw BizException.badRequest("不能给过去的日期点餐");
        if (menu != null && (menu.getStatus() == MenuStatus.CONFIRMED || menu.getStatus() == MenuStatus.READY)) {
            throw new BizException(HttpStatus.CONFLICT, "MENU_CONFIRMED", "菜单已确认，如需修改请联系掌厨重新开放");
        }
        if (me.getRole().canCook()) return;
        if (!slots.openForMembers(f, menu, date, type)) {
            throw new BizException(HttpStatus.CONFLICT, "WISH_CLOSED", "已过点餐截止时间");
        }
    }

    public List<WishView> toViews(Long familyId, List<MealWish> list, FamilyMember me) {
        if (list.isEmpty()) return List.of();
        Family family = families.findById(familyId).orElseThrow();
        Map<Long, String> names = members.findByFamilyIdOrderByIdAsc(familyId).stream()
                .collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));
        Set<Long> dishIds = list.stream().map(MealWish::getDishId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Dish> dishMap = dishIds.isEmpty() ? Map.of() : dishes.findByFamilyIdAndIdIn(familyId, dishIds)
                .stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
        Map<String, Menu> menuCache = new HashMap<>();
        return list.stream().map(w -> {
            Menu menu = menuCache.computeIfAbsent(w.getMealDate() + "|" + w.getMealType(), k ->
                    menus.findByFamilyIdAndMealDateAndMealType(familyId, w.getMealDate(), w.getMealType()).orElse(null));
            boolean mine = w.getMemberId().equals(me.getId());
            boolean editable = (mine || me.getRole().canCook()) && !w.getMealDate().isBefore(slots.today())
                    && (me.getRole().canCook()
                        ? menu == null || menu.getStatus() == MenuStatus.OPEN || menu.getStatus() == MenuStatus.LOCKED
                        : slots.openForMembers(family, menu, w.getMealDate(), w.getMealType()));
            Dish d = w.getDishId() == null ? null : dishMap.get(w.getDishId());
            return new WishView(w.getId(), w.getMemberId(), names.getOrDefault(w.getMemberId(), "已退出成员"),
                    w.getMealDate(), w.getMealType(), w.getDishId(), d == null ? null : d.getName(), w.getFreeText(),
                    w.getIntensity(), w.getServings(), w.isNotEatingHome(), w.getNote(), editable, w.getUpdatedAt());
        }).toList();
    }

    private static String normalizeText(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
