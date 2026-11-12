package com.zzwen6.familymeal.template;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishRepository;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuItem;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.template.TemplateDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WeekTemplateService {
    private final WeekTemplateRepository templates;
    private final MealPlanItemRepository plans;
    private final FamilyAccess access;
    private final DishRepository dishes;
    private final MenuRepository menus;

    public WeekTemplateService(WeekTemplateRepository templates, MealPlanItemRepository plans,
                               FamilyAccess access, DishRepository dishes, MenuRepository menus) {
        this.templates = templates;
        this.plans = plans;
        this.access = access;
        this.dishes = dishes;
        this.menus = menus;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(Long familyId) {
        access.member(familyId);
        return templates.findWithItems(familyId).stream().map(t -> toView(familyId, t)).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(Long familyId, Long id) {
        access.member(familyId);
        WeekTemplate t = templates.findWithItemsById(id, familyId).orElseThrow(() -> BizException.notFound("周模板"));
        return toView(familyId, t);
    }

    @Transactional
    public TemplateView create(Long familyId, TemplateRequest req) {
        FamilyMember me = access.cook(familyId);
        WeekTemplate t = new WeekTemplate();
        t.setFamilyId(familyId);
        t.setCreatedBy(me.getId());
        apply(familyId, t, req);
        templates.save(t);
        return toView(familyId, t);
    }

    @Transactional
    public TemplateView update(Long familyId, Long id, TemplateRequest req) {
        access.cook(familyId);
        WeekTemplate t = templates.findWithItemsById(id, familyId).orElseThrow(() -> BizException.notFound("周模板"));
        apply(familyId, t, req);
        templates.saveAndFlush(t);
        return toView(familyId, t);
    }

    @Transactional
    public void delete(Long familyId, Long id) {
        access.cook(familyId);
        WeekTemplate t = templates.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("周模板"));
        templates.delete(t);
    }

    /** 把某已过去一周里已确认 / 已开饭的日常菜单另存为模板。不含聚餐。 */
    @Transactional
    public TemplateView fromWeek(Long familyId, FromWeekRequest req) {
        FamilyMember me = access.cook(familyId);
        LocalDate start = requireMonday(req.weekStart());
        LocalDate end = start.plusDays(6);
        List<Menu> week = menus.findWithItems(familyId, start, end).stream()
                .filter(m -> m.getStatus() == MenuStatus.CONFIRMED || m.getStatus() == MenuStatus.READY)
                .toList();
        if (week.isEmpty()) throw BizException.badRequest("这一周没有已确认的日常菜单，不能另存为模板");

        WeekTemplate t = new WeekTemplate();
        t.setFamilyId(familyId);
        t.setCreatedBy(me.getId());
        t.setName(blankToName(req.name(), start.format(DateTimeFormatter.ofPattern("M月d日")) + "那一周"));
        t.setNote(blankToNull(req.note()));
        int order = 0;
        for (Menu m : week) {
            int dow = m.getMealDate().getDayOfWeek().getValue();
            for (MenuItem mi : m.getItems()) {
                WeekTemplateItem it = new WeekTemplateItem();
                it.setTemplate(t);
                it.setDayOfWeek(dow);
                it.setMealType(m.getMealType());
                it.setDishId(mi.getDishId());
                it.setCustomName(mi.getDishId() == null ? mi.getCustomName() : null);
                it.setServings(Math.max(1, mi.getServings()));
                it.setSortOrder(order++);
                t.getItems().add(it);
            }
        }
        templates.save(t);
        return toView(familyId, t);
    }

    /**
     * 应用到指定周一开始的那一周：已确认 / 已开饭的餐次永不覆盖；
     * 已有计划的餐次默认跳过，overwrite=true 时才替换计划。
     */
    @Transactional
    public ApplyResult applyToWeek(Long familyId, Long templateId, ApplyRequest req) {
        access.cook(familyId);
        WeekTemplate t = templates.findWithItemsById(templateId, familyId)
                .orElseThrow(() -> BizException.notFound("周模板"));
        LocalDate start = requireMonday(req.weekStart());
        boolean overwrite = Boolean.TRUE.equals(req.overwrite());

        Map<String, List<WeekTemplateItem>> bySlot = t.getItems().stream()
                .collect(Collectors.groupingBy(i -> i.getDayOfWeek() + "|" + i.getMealType(), LinkedHashMap::new, Collectors.toList()));

        int applied = 0, skippedConfirmed = 0, skippedPlan = 0, written = 0;
        for (var e : bySlot.entrySet()) {
            WeekTemplateItem first = e.getValue().getFirst();
            LocalDate date = start.plusDays(first.getDayOfWeek() - 1L);
            MealType type = first.getMealType();
            Menu menu = menus.findByFamilyIdAndMealDateAndMealType(familyId, date, type).orElse(null);
            if (menu != null && (menu.getStatus() == MenuStatus.CONFIRMED || menu.getStatus() == MenuStatus.READY)) {
                skippedConfirmed++;
                continue;
            }
            boolean hasPlan = plans.existsByFamilyIdAndMealDateAndMealType(familyId, date, type);
            if (hasPlan && !overwrite) {
                skippedPlan++;
                continue;
            }
            if (hasPlan) plans.deleteByFamilyIdAndMealDateAndMealType(familyId, date, type);
            int order = 0;
            for (WeekTemplateItem src : e.getValue()) {
                MealPlanItem p = new MealPlanItem();
                p.setFamilyId(familyId);
                p.setMealDate(date);
                p.setMealType(type);
                p.setDishId(src.getDishId());
                p.setCustomName(src.getCustomName());
                p.setServings(src.getServings());
                p.setSortOrder(order++);
                p.setSourceTemplateId(t.getId());
                plans.save(p);
                written++;
            }
            applied++;
        }
        return new ApplyResult(start, overwrite, applied, skippedConfirmed, skippedPlan, written);
    }

    @Transactional(readOnly = true)
    public List<PlanItemView> plans(Long familyId, LocalDate from, LocalDate to) {
        access.member(familyId);
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(62))) {
            throw BizException.badRequest("日期范围不正确（最多 62 天）");
        }
        return toPlanViews(familyId, plans.findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAscSortOrderAscIdAsc(
                familyId, from, to));
    }

    @Transactional(readOnly = true)
    public List<PlanItemView> plansOf(Long familyId, LocalDate date, MealType type) {
        return toPlanViews(familyId, plans.findByFamilyIdAndMealDateAndMealTypeOrderBySortOrderAscIdAsc(
                familyId, date, type));
    }

    private void apply(Long familyId, WeekTemplate t, TemplateRequest req) {
        t.setName(req.name().trim());
        t.setNote(blankToNull(req.note()));
        t.getItems().clear();
        if (req.items() == null || req.items().isEmpty()) return;
        int order = 0;
        for (TemplateItemInput in : req.items()) {
            if (in.dayOfWeek() < 1 || in.dayOfWeek() > 7) throw BizException.badRequest("星期应为 1（周一）到 7（周日）");
            String custom = normalizeText(in.customName());
            if (in.dishId() == null && custom == null) throw BizException.badRequest("每道菜需要选择菜谱或填写菜名");
            if (in.dishId() != null) {
                dishes.findByIdAndFamilyId(in.dishId(), familyId).orElseThrow(() -> BizException.notFound("菜品"));
            }
            WeekTemplateItem it = new WeekTemplateItem();
            it.setTemplate(t);
            it.setDayOfWeek(in.dayOfWeek());
            it.setMealType(in.mealType());
            it.setDishId(in.dishId());
            it.setCustomName(in.dishId() == null ? custom : null);
            it.setServings(in.servings() == null ? 1 : in.servings());
            it.setSortOrder(order++);
            t.getItems().add(it);
        }
    }

    private TemplateView toView(Long familyId, WeekTemplate t) {
        Set<Long> ids = t.getItems().stream().map(WeekTemplateItem::getDishId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Dish> dishMap = dishMap(familyId, ids);
        List<TemplateItemView> items = t.getItems().stream().map(i -> {
            Dish d = i.getDishId() == null ? null : dishMap.get(i.getDishId());
            String name = d != null ? d.getName() : (i.getCustomName() == null ? "未命名" : i.getCustomName());
            return new TemplateItemView(i.getId(), i.getDayOfWeek(), i.getMealType(), i.getDishId(), name,
                    i.getServings(), d == null ? null : d.getCoverUrl());
        }).toList();
        return new TemplateView(t.getId(), t.getName(), t.getNote(), items);
    }

    private List<PlanItemView> toPlanViews(Long familyId, List<MealPlanItem> list) {
        Set<Long> ids = list.stream().map(MealPlanItem::getDishId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Dish> dishMap = dishMap(familyId, ids);
        return list.stream().map(p -> {
            Dish d = p.getDishId() == null ? null : dishMap.get(p.getDishId());
            String name = d != null ? d.getName() : (p.getCustomName() == null ? "未命名" : p.getCustomName());
            return new PlanItemView(p.getId(), p.getMealDate(), p.getMealType(), p.getDishId(), name,
                    p.getServings(), d == null ? null : d.getCoverUrl(), p.getSourceTemplateId());
        }).toList();
    }

    private Map<Long, Dish> dishMap(Long familyId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return dishes.findByFamilyIdAndIdIn(familyId, ids).stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    private static LocalDate requireMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw BizException.badRequest("请指定那一周的周一作为起始日");
        }
        return weekStart;
    }

    private static String blankToName(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normalizeText(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
