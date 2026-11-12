package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.*;
import com.zzwen6.familymeal.dish.Dish;
import com.zzwen6.familymeal.dish.DishRepository;
import com.zzwen6.familymeal.family.*;
import com.zzwen6.familymeal.hint.HintDtos.HintView;
import com.zzwen6.familymeal.hint.MealHintService;
import com.zzwen6.familymeal.hint.MealHintService.DishRef;
import com.zzwen6.familymeal.inventory.InventoryDtos.LeftoverView;
import com.zzwen6.familymeal.inventory.LeftoverRepository;
import com.zzwen6.familymeal.inventory.LeftoverService;
import com.zzwen6.familymeal.menu.MenuDtos.*;
import com.zzwen6.familymeal.notify.NotificationService;
import com.zzwen6.familymeal.rating.MealRatingRepository;
import com.zzwen6.familymeal.rating.MealRatingService;
import com.zzwen6.familymeal.rating.RatingDtos.RatingView;
import com.zzwen6.familymeal.rotation.CookAssignment;
import com.zzwen6.familymeal.rotation.KitchenDuty;
import com.zzwen6.familymeal.shopping.ShoppingService;
import com.zzwen6.familymeal.template.TemplateDtos.PlanItemView;
import com.zzwen6.familymeal.template.WeekTemplateService;
import com.zzwen6.familymeal.wish.MealWish;
import com.zzwen6.familymeal.wish.MealWishRepository;
import com.zzwen6.familymeal.wish.WishService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MenuService {
    private static final Map<MealType, String> MEAL_NAMES = Map.of(
            MealType.BREAKFAST, "早餐", MealType.LUNCH, "午餐", MealType.DINNER, "晚餐");

    private final MenuRepository menus;
    private final MenuSlots slots;
    private final FamilyAccess access;
    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final MealWishRepository wishRepo;
    private final WishService wishService;
    private final DishRepository dishes;
    private final ShoppingService shopping;
    private final NotificationService notifications;
    private final KitchenDuty duty;
    private final MealHintService hints;
    private final LeftoverService leftoverService;
    private final LeftoverRepository leftoverRepo;
    private final WeekTemplateService templates;
    private final MealRatingService ratingService;
    private final MealRatingRepository ratingRepo;

    public MenuService(MenuRepository menus, MenuSlots slots, FamilyAccess access, FamilyRepository families,
                       FamilyMemberRepository members, MealWishRepository wishRepo, WishService wishService,
                       DishRepository dishes, ShoppingService shopping, NotificationService notifications,
                       KitchenDuty duty, MealHintService hints, LeftoverService leftoverService,
                       LeftoverRepository leftoverRepo, WeekTemplateService templates,
                       MealRatingService ratingService, MealRatingRepository ratingRepo) {
        this.menus = menus;
        this.slots = slots;
        this.access = access;
        this.families = families;
        this.members = members;
        this.wishRepo = wishRepo;
        this.wishService = wishService;
        this.dishes = dishes;
        this.shopping = shopping;
        this.notifications = notifications;
        this.duty = duty;
        this.hints = hints;
        this.leftoverService = leftoverService;
        this.leftoverRepo = leftoverRepo;
        this.templates = templates;
        this.ratingService = ratingService;
        this.ratingRepo = ratingRepo;
    }

    @Transactional(readOnly = true)
    public BoardView board(Long familyId, LocalDate date, MealType type) {
        FamilyMember me = access.member(familyId);
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        Menu menu = menus.findByFamilyIdAndMealDateAndMealType(familyId, date, type).orElse(null);
        List<MealWish> list = wishRepo.findByFamilyIdAndMealDateAndMealTypeOrderByIdAsc(familyId, date, type);
        List<FamilyMember> all = members.findByFamilyIdOrderByIdAsc(familyId);
        Map<Long, String> names = all.stream().collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));

        Set<Long> submitted = list.stream().map(MealWish::getMemberId).collect(Collectors.toSet());
        Set<Long> away = list.stream().filter(MealWish::isNotEatingHome).map(MealWish::getMemberId).collect(Collectors.toSet());
        List<MemberStatus> statuses = all.stream().map(m -> new MemberStatus(m.getId(), m.getDisplayName(), m.getRole(),
                submitted.contains(m.getId()), away.contains(m.getId()), m.getAllergies(), m.getDietaryNotes())).toList();
        int headcount = (int) all.stream().filter(m -> !away.contains(m.getId())).count();

        Set<Long> dishIds = list.stream().map(MealWish::getDishId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Dish> dishMap = dishIds.isEmpty() ? Map.of() : dishes.findByFamilyIdAndIdIn(familyId, dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        LinkedHashMap<String, VoteAcc> votes = new LinkedHashMap<>();
        for (MealWish w : list) {
            if (w.isNotEatingHome()) continue;
            String key = w.getDishId() != null ? "d" + w.getDishId() : "t" + w.getFreeText();
            String name = w.getDishId() != null
                    ? Optional.ofNullable(dishMap.get(w.getDishId())).map(Dish::getName).orElse("已删除菜品")
                    : w.getFreeText();
            VoteAcc v = votes.computeIfAbsent(key, k -> new VoteAcc(w.getDishId(), name));
            String who = names.getOrDefault(w.getMemberId(), "已退出成员");
            switch (w.getIntensity()) {
                case WANT -> { v.want++; v.servings += w.getServings(); v.wantBy.add(who); }
                case OK -> { v.ok++; v.servings += w.getServings(); }
                case AVOID -> { v.avoid++; v.avoidBy.add(who); }
            }
        }
        List<DishVote> voteList = votes.values().stream()
                .sorted(Comparator.comparingInt((VoteAcc v) -> v.want * 2 + v.ok - v.avoid * 2).reversed())
                .map(v -> new DishVote(v.dishId, v.name, v.want, v.ok, v.avoid, v.servings, v.wantBy, v.avoidBy))
                .toList();

        boolean canEdit = !date.isBefore(slots.today()) && (me.getRole().canCook()
                ? menu == null || menu.getStatus() == MenuStatus.OPEN || menu.getStatus() == MenuStatus.LOCKED
                : slots.openForMembers(family, menu, date, type));
        CookAssignment assigned = duty.assignment(familyId, date, type).orElse(null);
        CookBrief cookBrief = cookBrief(assigned, all);
        boolean proxy = assigned != null && menu != null && menu.getConfirmedBy() != null
                && !menu.getConfirmedBy().equals(assigned.getMemberId());
        List<DishRef> onBoard = new ArrayList<>();
        if (menu != null) onBoard.addAll(hints.refsOf(familyId, menu.getItems()));
        for (DishVote v : voteList) onBoard.add(new DishRef(v.dishId(), v.name()));
        List<HintView> hintList = hints.forBoard(family, date, type, onBoard);
        List<LeftoverView> leftoverList = leftoverRepo.findByFamilyIdAndUsedFalseOrderByExpiresOnAscIdAsc(familyId)
                .stream().map(LeftoverView::of).toList();
        List<PlanItemView> planItems = templates.plansOf(familyId, date, type);
        List<RatingView> ratingList = List.of();
        RatingView myRating = null;
        if (menu != null && menu.getStatus() == MenuStatus.READY) {
            ratingList = ratingService.toViews(familyId,
                    ratingRepo.findByFamilyIdAndMenuIdOrderByIdAsc(familyId, menu.getId()), me);
            myRating = ratingList.stream().filter(r -> r.memberId().equals(me.getId())).findFirst().orElse(null);
        }
        return new BoardView(date, type, menu == null ? null : view(family, menu, list.size()), canEdit,
                me.getRole().canCook(), duty.canRunKitchen(me, date, type),
                duty.canManageAssignment(me, date, type),
                slots.deadline(family, menu, date, type), headcount, statuses, voteList,
                wishService.toViews(familyId, list, me), cookBrief, proxy, hintList, leftoverList,
                planItems, ratingList, myRating);
    }

    @Transactional(readOnly = true)
    public List<MenuView> calendar(Long familyId, LocalDate from, LocalDate to) {
        access.member(familyId);
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) throw BizException.badRequest("日期范围不正确（最多 62 天）");
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        Map<String, Long> counts = wishRepo.findByFamilyIdAndMealDateBetweenOrderByMealDateAscIdAsc(familyId, from, to)
                .stream().collect(Collectors.groupingBy(w -> w.getMealDate() + "|" + w.getMealType(), Collectors.counting()));
        return menus.findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(familyId, from, to).stream()
                .map(m -> view(family, m, counts.getOrDefault(m.getMealDate() + "|" + m.getMealType(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuView get(Long familyId, Long menuId) {
        access.member(familyId);
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        Menu m = find(familyId, menuId);
        int count = wishRepo.findByFamilyIdAndMealDateAndMealTypeOrderByIdAsc(familyId, m.getMealDate(), m.getMealType()).size();
        return view(family, m, count);
    }

    /** 掌厨确认菜单（接受 / 改菜 / 替代），随后自动生成采购清单并发站内通知。 */
    @Transactional
    public ConfirmResponse confirm(Long familyId, ConfirmRequest req) {
        FamilyMember me = access.member(familyId);
        duty.requireKitchen(me, req.mealDate(), req.mealType());
        if (req.mealDate().isBefore(slots.today())) throw BizException.badRequest("不能确认过去日期的菜单");
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        Menu menu = slots.ensure(familyId, req.mealDate(), req.mealType());
        if (menu.getStatus() == MenuStatus.READY) {
            throw new BizException(HttpStatus.CONFLICT, "MENU_READY", "这餐已经开饭了");
        }
        List<MealWish> wishes = wishRepo.findByFamilyIdAndMealDateAndMealTypeOrderByIdAsc(
                familyId, req.mealDate(), req.mealType());
        int headcount = Math.max(1, (int) members.findByFamilyIdOrderByIdAsc(familyId).stream()
                .filter(m -> wishes.stream().noneMatch(w -> w.isNotEatingHome() && w.getMemberId().equals(m.getId())))
                .count());

        Set<Long> ids = new HashSet<>();
        req.items().forEach(i -> {
            if (i.dishId() != null) ids.add(i.dishId());
            if (i.substituteForDishId() != null) ids.add(i.substituteForDishId());
        });
        Map<Long, Dish> dishMap = ids.isEmpty() ? Map.of() : dishes.findByFamilyIdAndIdIn(familyId, ids).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));

        menu.getItems().clear();
        menus.flush();
        int order = 0;
        for (ConfirmItem ci : req.items()) {
            String custom = ci.customName() == null || ci.customName().isBlank() ? null : ci.customName().trim();
            if (ci.dishId() == null && custom == null) throw BizException.badRequest("每道菜需要选择菜谱或填写菜名");
            if (ci.dishId() != null && !dishMap.containsKey(ci.dishId())) throw BizException.notFound("菜品");
            if (ci.substituteForDishId() != null && !dishMap.containsKey(ci.substituteForDishId())) {
                throw BizException.notFound("被替代的菜品");
            }
            MenuItem mi = new MenuItem();
            mi.setMenu(menu);
            mi.setDishId(ci.dishId());
            mi.setCustomName(ci.dishId() == null ? custom : null);
            mi.setServings(ci.servings() == null ? headcount : ci.servings());
            MenuItemSource source = ci.source() != null ? ci.source()
                    : ci.substituteForDishId() != null ? MenuItemSource.SUBSTITUTE
                    : wishes.stream().anyMatch(w -> ci.dishId() != null && ci.dishId().equals(w.getDishId()))
                      ? MenuItemSource.WISH : MenuItemSource.COOK_ADDED;
            mi.setSource(source);
            mi.setSubstituteForDishId(ci.substituteForDishId());
            mi.setNote(ci.note() == null || ci.note().isBlank() ? null : ci.note().trim());
            mi.setSortOrder(order++);
            menu.getItems().add(mi);
        }
        menu.setStatus(MenuStatus.CONFIRMED);
        menu.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        menu.setConfirmedBy(me.getId());
        menu.setConfirmedAt(slots.now());
        menus.saveAndFlush(menu);

        var list = shopping.generate(menu);
        String dishNames = menu.getItems().stream().map(i -> itemName(i, dishMap)).collect(Collectors.joining("、"));
        leftoverService.useAll(familyId, req.useLeftoverIds(), menu.getId());
        leftoverService.autoUseMatching(familyId, menu.getId(),
                menu.getItems().stream().map(i -> itemName(i, dishMap)).toList());
        notifications.publish(familyId, menu.getId(), NotificationType.MENU_CONFIRMED,
                slotLabel(menu) + "菜单已确认", dishNames, me.getUserId());
        List<HintView> hintList = hints.forItems(family, req.mealDate(), req.mealType(),
                hints.refsOf(familyId, menu.getItems()));
        return new ConfirmResponse(view(family, menu, wishes.size()), shopping.get(familyId, list.getMenuId()), hintList);
    }

    @Transactional(readOnly = true)
    public HintPreview preview(Long familyId, ConfirmRequest req) {
        FamilyMember me = access.member(familyId);
        duty.requireKitchen(me, req.mealDate(), req.mealType());
        Family family = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        List<DishRef> refs = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        req.items().forEach(i -> {
            if (i.dishId() != null) ids.add(i.dishId());
        });
        Map<Long, Dish> dishMap = ids.isEmpty() ? Map.of() : dishes.findByFamilyIdAndIdIn(familyId, ids).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        for (ConfirmItem ci : req.items()) {
            String custom = ci.customName() == null || ci.customName().isBlank() ? null : ci.customName().trim();
            String name = ci.dishId() != null
                    ? Optional.ofNullable(dishMap.get(ci.dishId())).map(Dish::getName).orElse(custom)
                    : custom;
            refs.add(new DishRef(ci.dishId(), name));
        }
        return new HintPreview(hints.forItems(family, req.mealDate(), req.mealType(), refs));
    }

    /** 提前截止（不再接受成员修改）。 */
    @Transactional
    public MenuView lock(Long familyId, Long menuId) {
        FamilyMember me = access.member(familyId);
        Menu m = find(familyId, menuId);
        duty.requireKitchen(me, m.getMealDate(), m.getMealType());
        if (m.getStatus() != MenuStatus.OPEN) throw BizException.conflict("BAD_STATUS", "只有可点餐的餐次能截止");
        m.setStatus(MenuStatus.LOCKED);
        return get(familyId, menuId);
    }

    /** 重新开放点餐，默认延长 1 小时；已确认的菜单回到可点状态，采购清单在下次确认时重新生成。 */
    @Transactional
    public MenuView reopen(Long familyId, Long menuId, ReopenRequest req) {
        FamilyMember me = access.member(familyId);
        Menu m = find(familyId, menuId);
        duty.requireKitchen(me, m.getMealDate(), m.getMealType());
        if (m.getStatus() == MenuStatus.READY) throw BizException.conflict("MENU_READY", "这餐已经开饭了");
        LocalDateTime deadline = req != null && req.deadline() != null ? req.deadline() : slots.now().plusHours(1);
        if (!deadline.isAfter(slots.now())) throw BizException.badRequest("新的截止时间要晚于现在");
        m.setStatus(MenuStatus.OPEN);
        m.setDeadlineOverride(deadline);
        return get(familyId, menuId);
    }

    @Transactional
    public MenuView reopenSlot(Long familyId, ReopenSlotRequest req) {
        FamilyMember me = access.member(familyId);
        duty.requireKitchen(me, req.mealDate(), req.mealType());
        if (req.mealDate().isBefore(slots.today())) throw BizException.badRequest("不能重新开放过去的餐次");
        Menu m = slots.ensure(familyId, req.mealDate(), req.mealType());
        return reopen(familyId, m.getId(), new ReopenRequest(req.deadline()));
    }

    /** 开饭啦：写站内通知，并尝试发微信订阅消息（未配置时跳过）。 */
    @Transactional
    public MenuView ready(Long familyId, Long menuId) {
        FamilyMember me = access.member(familyId);
        Menu m = find(familyId, menuId);
        duty.requireKitchen(me, m.getMealDate(), m.getMealType());
        if (m.getStatus() != MenuStatus.CONFIRMED) throw BizException.conflict("BAD_STATUS", "请先确认菜单");
        m.setStatus(MenuStatus.READY);
        m.setReadyAt(slots.now());
        notifications.publish(familyId, m.getId(), NotificationType.DINNER_READY, "开饭啦",
                slotLabel(m) + "做好了，快来吃！", me.getUserId());
        return get(familyId, menuId);
    }

    private Menu find(Long familyId, Long menuId) {
        return menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
    }

    private MenuView view(Family family, Menu m, int wishCount) {
        Set<Long> ids = new HashSet<>();
        m.getItems().forEach(i -> {
            if (i.getDishId() != null) ids.add(i.getDishId());
            if (i.getSubstituteForDishId() != null) ids.add(i.getSubstituteForDishId());
        });
        Map<Long, Dish> dishMap = ids.isEmpty() ? Map.of() : dishes.findByFamilyIdAndIdIn(m.getFamilyId(), ids).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        List<MenuItemView> items = m.getItems().stream().map(i -> {
            Dish d = i.getDishId() == null ? null : dishMap.get(i.getDishId());
            Dish sub = i.getSubstituteForDishId() == null ? null : dishMap.get(i.getSubstituteForDishId());
            return new MenuItemView(i.getId(), i.getDishId(), itemName(i, dishMap), i.getServings(), i.getSource(),
                    i.getSubstituteForDishId(), sub == null ? null : sub.getName(), i.getNote(),
                    d == null ? null : d.getCoverUrl(), d == null ? null : d.getDurationMinutes(),
                    d == null ? null : d.getCategory());
        }).toList();
        String confirmedBy = m.getConfirmedBy() == null ? null
                : members.findById(m.getConfirmedBy()).map(FamilyMember::getDisplayName).orElse(null);
        CookAssignment assigned = duty.assignment(m.getFamilyId(), m.getMealDate(), m.getMealType()).orElse(null);
        Long assignedId = assigned == null ? null : assigned.getMemberId();
        String assignedName = assignedId == null ? null
                : members.findById(assignedId).map(FamilyMember::getDisplayName).orElse("已退出成员");
        String assignedSource = assigned == null ? null : assigned.getSource().name();
        boolean proxy = assignedId != null && m.getConfirmedBy() != null && !assignedId.equals(m.getConfirmedBy());
        return new MenuView(m.getId(), m.getMealDate(), m.getMealType(), m.getStatus(),
                slots.deadline(family, m, m.getMealDate(), m.getMealType()), m.getNote(), m.getConfirmedAt(),
                confirmedBy, m.getReadyAt(), items, wishCount,
                assignedId, assignedName, assignedSource, proxy);
    }

    private static CookBrief cookBrief(CookAssignment assigned, List<FamilyMember> all) {
        if (assigned == null) return null;
        String name = all.stream().filter(x -> x.getId().equals(assigned.getMemberId()))
                .map(FamilyMember::getDisplayName).findFirst().orElse("已退出成员");
        return new CookBrief(assigned.getMemberId(), name, assigned.getSource().name());
    }

    private static String itemName(MenuItem i, Map<Long, Dish> dishMap) {
        if (i.getDishId() != null && dishMap.containsKey(i.getDishId())) return dishMap.get(i.getDishId()).getName();
        return i.getCustomName() == null ? "未命名" : i.getCustomName();
    }

    private static String slotLabel(Menu m) {
        return m.getMealDate().format(DateTimeFormatter.ofPattern("M月d日")) + MEAL_NAMES.get(m.getMealType());
    }

    private static final class VoteAcc {
        final Long dishId;
        final String name;
        int want, ok, avoid, servings;
        final List<String> wantBy = new ArrayList<>();
        final List<String> avoidBy = new ArrayList<>();

        VoteAcc(Long dishId, String name) {
            this.dishId = dishId;
            this.name = name;
        }
    }
}
