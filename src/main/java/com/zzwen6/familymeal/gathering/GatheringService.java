package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.auth.AppUser;
import com.zzwen6.familymeal.auth.AppUserRepository;
import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.NotificationType;
import com.zzwen6.familymeal.dish.Amounts;
import com.zzwen6.familymeal.family.FamilyMemberRepository;
import com.zzwen6.familymeal.gathering.Gathering.Mode;
import com.zzwen6.familymeal.gathering.Gathering.Status;
import com.zzwen6.familymeal.gathering.GatheringAccess.Ctx;
import com.zzwen6.familymeal.gathering.GatheringDtos.*;
import com.zzwen6.familymeal.gathering.GatheringParticipant.Rsvp;
import com.zzwen6.familymeal.notify.NotificationService;
import com.zzwen6.familymeal.notify.NotificationService.NotificationView;
import com.zzwen6.familymeal.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 临时聚餐。三种玩法在「点菜」这一步是不同的：
 * <ul>
 *   <li>HOST_COOK 主人掌厨：客人提交想吃什么（同一道菜可以多人想吃，按人汇总）；主人定菜单、维护采购清单；</li>
 *   <li>VOTE 众筹投票：大家提议菜名并投票（一人一菜一票，同名菜不能重复提议）；主人按票数定菜单；</li>
 *   <li>POTLUCK 各带一道：每人认领自己带的菜（同名菜不能重复认领）；主人看谁带什么、谁还没认领，再确认。</li>
 * </ul>
 * 数据边界见 {@code package-info.java}。
 */
@Service
public class GatheringService {
    private static final Map<MealType, String> MEAL_NAMES = Map.of(
            MealType.BREAKFAST, "早餐", MealType.LUNCH, "午餐", MealType.DINNER, "晚餐");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GatheringRepository gatherings;
    private final GatheringParticipantRepository participants;
    private final GatheringWishRepository wishes;
    private final GatheringVoteRepository votes;
    private final GatheringMenuItemRepository menuItems;
    private final GatheringShoppingItemRepository shoppingItems;
    private final GatheringAccess access;
    private final AppUserRepository users;
    private final FamilyMemberRepository familyMembers;
    private final NotificationService notifications;
    private final Clock clock;

    public GatheringService(GatheringRepository gatherings, GatheringParticipantRepository participants,
                            GatheringWishRepository wishes, GatheringVoteRepository votes,
                            GatheringMenuItemRepository menuItems, GatheringShoppingItemRepository shoppingItems,
                            GatheringAccess access, AppUserRepository users, FamilyMemberRepository familyMembers,
                            NotificationService notifications, Clock clock) {
        this.gatherings = gatherings;
        this.participants = participants;
        this.wishes = wishes;
        this.votes = votes;
        this.menuItems = menuItems;
        this.shoppingItems = shoppingItems;
        this.access = access;
        this.users = users;
        this.familyMembers = familyMembers;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ======================= 我的聚餐 / 创建 / 修改 / 复用 =======================

    /** 我发起的和我参加的（含已散场），按日期倒序。 */
    @Transactional(readOnly = true)
    public List<GatheringSummary> mine() {
        Long uid = CurrentUser.id();
        Map<Long, GatheringParticipant> mineByG = participants.findByUserId(uid).stream()
                .collect(Collectors.toMap(GatheringParticipant::getGatheringId, Function.identity(), (a, b) -> a));
        if (mineByG.isEmpty()) return List.of();
        List<Gathering> list = gatherings.findByIdInOrderByMealDateDescIdDesc(mineByG.keySet());
        Map<Long, String> hostNames = hostNames(list);
        return list.stream().map(g -> {
            List<GatheringParticipant> ps = participants.findByGatheringIdOrderByIdAsc(g.getId());
            return new GatheringSummary(g.getId(), g.getTitle(), g.getMealDate(), g.getMealType(), g.getLocation(),
                    g.getMode(), g.getStatus(), hostNames.get(g.getHostUserId()), g.getHostUserId().equals(uid),
                    mineByG.get(g.getId()).getRsvp(), headcount(ps), ps.size(), g.getReadyAt());
        }).toList();
    }

    @Transactional
    public GatheringDetail create(CreateRequest req) {
        AppUser me = currentUser();
        if (req.mealDate().isBefore(today())) throw BizException.badRequest("不能约在过去的日子");
        checkHostFamily(req.hostFamilyId());
        Gathering g = new Gathering();
        g.setHostUserId(me.getId());
        g.setHostFamilyId(req.hostFamilyId());
        g.setTitle(req.title().trim());
        g.setMealDate(req.mealDate());
        g.setMealType(req.mealType());
        g.setLocation(blankToNull(req.location()));
        g.setNote(blankToNull(req.note()));
        g.setMode(req.mode());
        g.setStatus(Status.OPEN);
        g.setDeadlineAt(req.deadlineAt());
        g.setInviteToken(newToken());
        gatherings.save(g);

        GatheringParticipant host = new GatheringParticipant();
        host.setGatheringId(g.getId());
        host.setUserId(me.getId());
        host.setGuestName(me.getNickname());
        host.setHeadcount(req.headcount() == null ? 1 : req.headcount());
        host.setDietaryNotes(blankToNull(req.dietaryNotes()));
        host.setRsvp(Rsvp.YES);
        participants.save(host);
        return detail(g, host);
    }

    @Transactional
    public GatheringDetail update(Long id, UpdateRequest req) {
        Ctx c = access.host(id);
        Gathering g = c.gathering();
        requireNotClosed(g);
        if (!req.mealDate().equals(g.getMealDate()) && req.mealDate().isBefore(today())) {
            throw BizException.badRequest("不能改到过去的日子");
        }
        if (!Objects.equals(req.hostFamilyId(), g.getHostFamilyId())) checkHostFamily(req.hostFamilyId());
        g.setTitle(req.title().trim());
        g.setMealDate(req.mealDate());
        g.setMealType(req.mealType());
        g.setLocation(blankToNull(req.location()));
        g.setDeadlineAt(req.deadlineAt());
        g.setHostFamilyId(req.hostFamilyId());
        g.setNote(blankToNull(req.note()));
        return detail(g, c.me());
    }

    /** 复用「上次那波人」：发起人可以从自己发起的任意一场（包括已散场的）新建。 */
    @Transactional
    public GatheringDetail copy(Long sourceId, CopyRequest req) {
        Ctx c = access.host(sourceId);
        Gathering src = c.gathering();
        GatheringDetail created = create(new CreateRequest(req.title(), req.mealDate(), req.mealType(),
                req.location() != null ? req.location() : src.getLocation(),
                req.mode() != null ? req.mode() : src.getMode(), req.deadlineAt(), req.hostFamilyId(), req.note(),
                c.me().getHeadcount(), c.me().getDietaryNotes()));
        Gathering g = gatherings.findById(created.id()).orElseThrow();
        g.setSourceGatheringId(src.getId());
        for (GatheringParticipant old : participants.findByGatheringIdOrderByIdAsc(src.getId())) {
            if (old.getId().equals(c.me().getId())) continue;
            GatheringParticipant p = new GatheringParticipant();
            p.setGatheringId(g.getId());
            p.setUserId(old.getUserId());
            p.setGuestName(old.getGuestName());
            p.setHeadcount(old.getHeadcount());
            p.setDietaryNotes(old.getDietaryNotes());
            p.setRsvp(Rsvp.MAYBE); // 等大家重新应邀
            participants.save(p);
        }
        GatheringParticipant host = participants.findByGatheringIdAndUserId(g.getId(), c.me().getUserId()).orElseThrow();
        return detail(g, host);
    }

    // ======================= 邀请 / 应邀 =======================

    @Transactional(readOnly = true)
    public InvitePreview preview(String token) {
        Gathering g = byToken(token);
        GatheringParticipant mine = participants.findByGatheringIdAndUserId(g.getId(), CurrentUser.id()).orElse(null);
        String host = users.findById(g.getHostUserId()).map(AppUser::getNickname).orElse("发起人");
        boolean joinable = !g.isClosed();
        return new InvitePreview(g.getTitle(), g.getMealDate(), g.getMealType(), g.getLocation(), g.getMode(),
                g.getStatus(), host, g.getDeadlineAt(), joinable, mine != null, mine == null ? null : g.getId(),
                joinable ? null : "这场聚餐已经散场，邀请已失效");
    }

    /** 通过邀请加入（已经在了就当作更新应邀）。加入只成为本场参与者，不会加入任何家庭。 */
    @Transactional
    public GatheringDetail join(String token, JoinRequest req) {
        Gathering g = byToken(token);
        requireNotClosed(g);
        AppUser me = currentUser();
        GatheringParticipant p = participants.findByGatheringIdAndUserId(g.getId(), me.getId()).orElse(null);
        if (p == null) {
            p = new GatheringParticipant();
            p.setGatheringId(g.getId());
            p.setUserId(me.getId());
            p.setGuestName(me.getNickname());
            p.setRsvp(Rsvp.YES);
        }
        if (req != null) {
            if (req.rsvp() != null) p.setRsvp(req.rsvp());
            if (req.headcount() != null) p.setHeadcount(req.headcount());
            if (req.dietaryNotes() != null) p.setDietaryNotes(blankToNull(req.dietaryNotes()));
            if (req.guestName() != null && !req.guestName().isBlank()) p.setGuestName(req.guestName().trim());
        }
        participants.saveAndFlush(p);
        return detail(g, p);
    }

    /** 改自己的应邀。截止后也能改人数（菜单份量和采购会按新人数换算），散场后不能改。 */
    @Transactional
    public GatheringDetail updateRsvp(Long id, RsvpRequest req) {
        Ctx c = access.participant(id);
        requireNotClosed(c.gathering());
        GatheringParticipant p = c.me();
        if (c.isHost() && req.rsvp() == Rsvp.NO) throw BizException.badRequest("发起人不能不来；不办了可以直接散场");
        p.setRsvp(req.rsvp());
        if (req.headcount() != null) p.setHeadcount(req.headcount());
        p.setDietaryNotes(blankToNull(req.dietaryNotes()));
        if (req.guestName() != null && !req.guestName().isBlank()) p.setGuestName(req.guestName().trim());
        return detail(c.gathering(), p);
    }

    @Transactional
    public GatheringDetail addGuest(Long id, AddGuestRequest req) {
        Ctx c = access.host(id);
        requireNotClosed(c.gathering());
        GatheringParticipant p = new GatheringParticipant();
        p.setGatheringId(id);
        p.setGuestName(req.guestName().trim());
        p.setHeadcount(req.headcount() == null ? 1 : req.headcount());
        p.setDietaryNotes(blankToNull(req.dietaryNotes()));
        p.setRsvp(req.rsvp() == null ? Rsvp.YES : req.rsvp());
        participants.save(p);
        return detail(c.gathering(), c.me());
    }

    @Transactional
    public GatheringDetail removeParticipant(Long id, Long participantId) {
        Ctx c = access.host(id);
        requireNotClosed(c.gathering());
        GatheringParticipant p = participants.findByIdAndGatheringId(participantId, id)
                .orElseThrow(() -> BizException.notFound("参与者"));
        if (p.getId().equals(c.me().getId())) throw BizException.badRequest("不能移除发起人自己");
        participants.delete(p); // 意愿、投票随外键级联删除，菜单里「谁带」置空
        participants.flush();
        recountVotes(id);
        return detail(c.gathering(), c.me());
    }

    // ======================= 按玩法点菜：意愿 / 提议+投票 / 认领 =======================

    @Transactional
    public GatheringDetail addWish(Long id, WishRequest req) {
        Ctx c = access.participant(id);
        Gathering g = c.gathering();
        requireWishOpen(g);
        String name = req.dishName().trim();
        List<GatheringWish> all = wishes.findByGatheringIdOrderByIdAsc(id);
        checkDuplicate(g, all, c.me(), name, null);
        GatheringWish w = new GatheringWish();
        w.setGatheringId(id);
        w.setParticipantId(c.me().getId());
        w.setDishName(name);
        w.setNote(blankToNull(req.note()));
        w.setBringBySelf(g.getMode() == Mode.POTLUCK);
        w.setVotes(0);
        wishes.saveAndFlush(w);
        if (g.getMode() == Mode.VOTE) {
            // 提议的人默认投自己一票
            votes.saveAndFlush(new GatheringVote(id, w.getId(), c.me().getId()));
            w.setVotes(1);
        }
        return detail(g, c.me());
    }

    @Transactional
    public GatheringDetail updateWish(Long id, Long wishId, WishRequest req) {
        Ctx c = access.participant(id);
        Gathering g = c.gathering();
        requireWishOpen(g);
        GatheringWish w = wish(id, wishId);
        if (!w.getParticipantId().equals(c.me().getId())) throw BizException.forbidden("只能改自己提交的菜");
        String name = req.dishName().trim();
        checkDuplicate(g, wishes.findByGatheringIdOrderByIdAsc(id), c.me(), name, w.getId());
        w.setDishName(name);
        w.setNote(blankToNull(req.note()));
        return detail(g, c.me());
    }

    /** 自己的可以删；发起人可以删任何一条（整理菜单用）。 */
    @Transactional
    public GatheringDetail deleteWish(Long id, Long wishId) {
        Ctx c = access.participant(id);
        Gathering g = c.gathering();
        requireNotClosed(g);
        GatheringWish w = wish(id, wishId);
        boolean mine = w.getParticipantId().equals(c.me().getId());
        if (!mine && !c.isHost()) throw BizException.forbidden("只能删自己提交的菜");
        if (!c.isHost()) requireWishOpen(g);
        wishes.delete(w);
        return detail(g, c.me());
    }

    @Transactional
    public GatheringDetail vote(Long id, Long wishId) {
        Ctx c = access.participant(id);
        Gathering g = c.gathering();
        requireMode(g, Mode.VOTE, "只有众筹投票的聚餐可以投票");
        requireWishOpen(g);
        GatheringWish w = wish(id, wishId);
        if (votes.findByWishIdAndParticipantId(wishId, c.me().getId()).isPresent()) {
            throw BizException.conflict("ALREADY_VOTED", "你已经投过这道菜了");
        }
        votes.saveAndFlush(new GatheringVote(id, wishId, c.me().getId()));
        w.setVotes((int) votes.countByWishId(wishId));
        return detail(g, c.me());
    }

    @Transactional
    public GatheringDetail unvote(Long id, Long wishId) {
        Ctx c = access.participant(id);
        Gathering g = c.gathering();
        requireMode(g, Mode.VOTE, "只有众筹投票的聚餐可以投票");
        requireWishOpen(g);
        GatheringWish w = wish(id, wishId);
        GatheringVote v = votes.findByWishIdAndParticipantId(wishId, c.me().getId())
                .orElseThrow(() -> BizException.notFound("投票"));
        votes.delete(v);
        votes.flush();
        w.setVotes((int) votes.countByWishId(wishId));
        return detail(g, c.me());
    }

    // ======================= 确认菜单 / 开饭 / 散场 =======================

    /** 发起人确认（或改）菜单；截止后、菜单已定后都还能改，散场后不能。 */
    @Transactional
    public GatheringDetail confirm(Long id, ConfirmRequest req) {
        Ctx c = access.host(id);
        Gathering g = c.gathering();
        requireNotClosed(g);
        Map<Long, GatheringWish> wishMap = wishes.findByGatheringIdOrderByIdAsc(id).stream()
                .collect(Collectors.toMap(GatheringWish::getId, Function.identity()));
        Set<Long> pids = participants.findByGatheringIdOrderByIdAsc(id).stream().map(GatheringParticipant::getId)
                .collect(Collectors.toSet());
        menuItems.deleteByGatheringId(id);
        menuItems.flush();
        int order = 0;
        for (ConfirmItem ci : req.items()) {
            GatheringWish w = null;
            if (ci.wishId() != null) {
                w = wishMap.get(ci.wishId());
                if (w == null) throw BizException.notFound("被采纳的菜");
            }
            String name = ci.dishName() != null && !ci.dishName().isBlank() ? ci.dishName().trim()
                    : w != null ? w.getDishName() : null;
            if (name == null) throw BizException.badRequest("每道菜需要填写菜名或选择一条意愿");
            Long bring = ci.bringParticipantId();
            if (bring != null && !pids.contains(bring)) throw BizException.notFound("带菜的参与者");
            if (bring == null && w != null && w.isBringBySelf()) bring = w.getParticipantId();
            GatheringMenuItem mi = new GatheringMenuItem();
            mi.setGatheringId(id);
            mi.setDishName(name);
            mi.setServings(ci.servings());
            mi.setWishId(w == null ? null : w.getId());
            mi.setBringParticipantId(bring);
            mi.setNote(blankToNull(ci.note()));
            mi.setSortOrder(order++);
            menuItems.save(mi);
        }
        List<GatheringParticipant> ps = participants.findByGatheringIdOrderByIdAsc(id);
        g.setStatus(Status.CONFIRMED);
        g.setConfirmedAt(now());
        g.setConfirmedHeadcount(headcount(ps));
        String names = req.items().stream().map(ci -> ci.dishName() != null && !ci.dishName().isBlank()
                ? ci.dishName().trim() : wishMap.get(ci.wishId()).getDishName()).collect(Collectors.joining("、"));
        notifications.publishGathering(id, yesUserIds(ps), NotificationType.GATHERING_CONFIRMED,
                cut(g.getTitle() + "菜单定了", 64), cut(names, 255), c.me().getUserId());
        return detail(g, c.me());
    }

    /** 菜单定了之后，发起人想让大家再改改：回到 OPEN（菜单保留，可设新截止时间）。 */
    @Transactional
    public GatheringDetail reopen(Long id, ReopenRequest req) {
        Ctx c = access.host(id);
        Gathering g = c.gathering();
        requireNotClosed(g);
        if (g.getReadyAt() != null) throw BizException.conflict("ALREADY_READY", "已经开饭了");
        LocalDateTime deadline = req == null ? null : req.deadlineAt();
        if (deadline != null && !deadline.isAfter(now())) throw BizException.badRequest("新的截止时间要晚于现在");
        g.setStatus(Status.OPEN);
        g.setDeadlineAt(deadline);
        return detail(g, c.me());
    }

    /** 开饭啦：给本场 RSVP=YES 的参与者写聚餐通知，并尝试发微信订阅消息（未配置时跳过）。 */
    @Transactional
    public GatheringDetail ready(Long id) {
        Ctx c = access.host(id);
        Gathering g = c.gathering();
        requireNotClosed(g);
        if (g.getStatus() != Status.CONFIRMED) throw BizException.conflict("BAD_STATUS", "请先确认菜单");
        if (g.getReadyAt() != null) throw BizException.conflict("ALREADY_READY", "已经通知过开饭了");
        g.setReadyAt(now());
        List<GatheringParticipant> ps = participants.findByGatheringIdOrderByIdAsc(id);
        notifications.publishGathering(id, yesUserIds(ps), NotificationType.GATHERING_READY, "开饭啦",
                cut(slotLabel(g) + "「" + g.getTitle() + "」开饭了，快来！", 255), c.me().getUserId());
        return detail(g, c.me());
    }

    /** 散场：邀请失效、本场只读；数据留在这场，不进任何家庭统计。 */
    @Transactional
    public GatheringDetail close(Long id) {
        Ctx c = access.host(id);
        Gathering g = c.gathering();
        requireNotClosed(g);
        g.setStatus(Status.CLOSED);
        g.setClosedAt(now());
        return detail(g, c.me());
    }

    @Transactional(readOnly = true)
    public GatheringDetail get(Long id) {
        Ctx c = access.participant(id);
        return detail(c.gathering(), c.me());
    }

    @Transactional(readOnly = true)
    public List<NotificationView> notifications(Long id, int limit) {
        access.participant(id);
        return notifications.recentForGathering(id, limit);
    }

    // ======================= 采购清单（主人掌厨 / 众筹投票） =======================

    @Transactional(readOnly = true)
    public ShoppingView shopping(Long id) {
        Ctx c = access.participant(id);
        requireShopping(c.gathering());
        return shoppingView(id);
    }

    @Transactional
    public ShoppingView addShoppingItem(Long id, ShoppingItemRequest req) {
        Ctx c = access.host(id);
        requireShopping(c.gathering());
        requireNotClosed(c.gathering());
        GatheringShoppingItem it = new GatheringShoppingItem();
        it.setGatheringId(id);
        it.setSortOrder(shoppingItems.findByGatheringIdOrderBySortOrderAscIdAsc(id).size());
        apply(it, req);
        shoppingItems.save(it);
        return shoppingView(id);
    }

    @Transactional
    public ShoppingView updateShoppingItem(Long id, Long itemId, ShoppingItemRequest req) {
        Ctx c = access.host(id);
        requireShopping(c.gathering());
        requireNotClosed(c.gathering());
        apply(shoppingItem(id, itemId), req);
        return shoppingView(id);
    }

    @Transactional
    public ShoppingView markPurchased(Long id, Long itemId, PurchasedRequest req) {
        Ctx c = access.host(id);
        requireShopping(c.gathering());
        requireNotClosed(c.gathering());
        shoppingItem(id, itemId).setPurchased(req.purchased());
        return shoppingView(id);
    }

    @Transactional
    public ShoppingView deleteShoppingItem(Long id, Long itemId) {
        Ctx c = access.host(id);
        requireShopping(c.gathering());
        requireNotClosed(c.gathering());
        shoppingItems.delete(shoppingItem(id, itemId));
        shoppingItems.flush();
        return shoppingView(id);
    }

    // ======================= 家庭日历占位 =======================

    /** 调用方（FamilyGatheringController）已校验是该家庭成员；这里只返回占位所需的最少信息。 */
    @Transactional(readOnly = true)
    public List<FamilySlotView> familySlots(Long familyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) throw BizException.badRequest("日期范围不正确（最多 62 天）");
        Long uid = CurrentUser.id();
        List<Gathering> list = gatherings.findByHostFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(familyId, from, to);
        Map<Long, String> hostNames = hostNames(list);
        return list.stream().map(g -> new FamilySlotView(g.getId(), g.getTitle(), g.getMealDate(), g.getMealType(),
                g.getStatus(), hostNames.get(g.getHostUserId()),
                participants.findByGatheringIdAndUserId(g.getId(), uid).isPresent())).toList();
    }

    // ======================= 内部 =======================

    private GatheringDetail detail(Gathering g, GatheringParticipant me) {
        participants.flush();
        boolean host = g.getHostUserId().equals(me.getUserId());
        List<GatheringParticipant> ps = participants.findByGatheringIdOrderByIdAsc(g.getId());
        Map<Long, String> names = ps.stream().collect(Collectors.toMap(GatheringParticipant::getId, GatheringParticipant::getGuestName));
        List<ParticipantView> pViews = ps.stream().map(p -> participantView(g, p, me, host)).toList();
        ParticipantView meView = participantView(g, me, me, host);
        int headcount = headcount(ps);
        boolean wishOpen = wishOpen(g);

        List<GatheringWish> ws = wishes.findByGatheringIdOrderByIdAsc(g.getId());
        Set<Long> myVotes = votes.findByGatheringId(g.getId()).stream()
                .filter(v -> v.getParticipantId().equals(me.getId())).map(GatheringVote::getWishId).collect(Collectors.toSet());
        Comparator<GatheringWish> order = g.getMode() == Mode.VOTE
                ? Comparator.comparingInt(GatheringWish::getVotes).reversed().thenComparing(GatheringWish::getId)
                : Comparator.comparing(GatheringWish::getId);
        List<WishView> wViews = ws.stream().sorted(order).map(w -> {
            boolean mine = w.getParticipantId().equals(me.getId());
            return new WishView(w.getId(), w.getParticipantId(), names.getOrDefault(w.getParticipantId(), "已退出"),
                    w.getDishName(), w.getNote(), w.isBringBySelf(), w.getVotes(), myVotes.contains(w.getId()), mine,
                    mine && wishOpen, w.getCreatedAt());
        }).toList();

        List<DishTally> tally = List.of();
        List<String> unclaimed = List.of();
        if (g.getMode() == Mode.HOST_COOK) {
            LinkedHashMap<String, List<GatheringWish>> groups = new LinkedHashMap<>();
            ws.forEach(w -> groups.computeIfAbsent(key(w.getDishName()), k -> new ArrayList<>()).add(w));
            tally = groups.values().stream()
                    .map(l -> new DishTally(l.get(0).getDishName(), l.size(),
                            l.stream().map(w -> names.getOrDefault(w.getParticipantId(), "已退出")).distinct().toList()))
                    .sorted(Comparator.comparingInt(DishTally::count).reversed())
                    .toList();
        } else if (g.getMode() == Mode.POTLUCK) {
            Set<Long> claimed = ws.stream().map(GatheringWish::getParticipantId).collect(Collectors.toSet());
            unclaimed = ps.stream().filter(p -> p.getRsvp() == Rsvp.YES && !claimed.contains(p.getId()))
                    .map(GatheringParticipant::getGuestName).toList();
        }

        int servingsAuto = Math.max(1, headcount);
        List<MenuItemView> menu = menuItems.findByGatheringIdOrderBySortOrderAscIdAsc(g.getId()).stream()
                .map(mi -> new MenuItemView(mi.getId(), mi.getDishName(),
                        mi.getServings() == null ? servingsAuto : mi.getServings(), mi.getServings() == null,
                        mi.getWishId(), mi.getBringParticipantId(),
                        mi.getBringParticipantId() == null ? null : names.get(mi.getBringParticipantId()), mi.getNote()))
                .toList();

        String hostName = users.findById(g.getHostUserId()).map(AppUser::getNickname).orElse("发起人");
        return new GatheringDetail(g.getId(), g.getTitle(), g.getMealDate(), g.getMealType(), g.getLocation(),
                g.getNote(), g.getMode(), g.getStatus(), hostName, host,
                host ? g.getHostFamilyId() : null, // 家庭信息只给发起人自己看
                g.isClosed() ? null : g.getInviteToken(), g.getDeadlineAt(), wishOpen, headcount,
                g.getConfirmedHeadcount(),
                g.getConfirmedHeadcount() != null && g.getStatus() != Status.OPEN && g.getConfirmedHeadcount() != headcount,
                count(ps, Rsvp.YES), count(ps, Rsvp.MAYBE), count(ps, Rsvp.NO), g.getConfirmedAt(), g.getReadyAt(),
                g.getClosedAt(), g.getCreatedAt(), g.getSourceGatheringId(), meView, pViews, wViews, tally, unclaimed, menu);
    }

    private static ParticipantView participantView(Gathering g, GatheringParticipant p, GatheringParticipant me, boolean iAmHost) {
        boolean isMe = p.getId().equals(me.getId());
        return new ParticipantView(p.getId(), p.getGuestName(), p.getRsvp(), p.getHeadcount(),
                iAmHost || isMe ? p.getDietaryNotes() : null,
                p.getUserId() != null && p.getUserId().equals(g.getHostUserId()), isMe, p.getUserId() == null,
                p.getJoinedAt());
    }

    private ShoppingView shoppingView(Long id) {
        int headcount = Math.max(1, headcount(participants.findByGatheringIdOrderByIdAsc(id)));
        List<ShoppingItemView> items = shoppingItems.findByGatheringIdOrderBySortOrderAscIdAsc(id).stream()
                .map(i -> new ShoppingItemView(i.getId(), i.getName(), i.getAmount(), i.getUnit(), i.getBaseServings(),
                        i.getBaseServings() == null ? Amounts.normalize(i.getAmount())
                                : Amounts.scale(i.getAmount(), headcount, i.getBaseServings(), i.getUnit()),
                        i.getForDish(), i.getNote(), i.isPurchased()))
                .toList();
        return new ShoppingView(id, headcount, items, items.size(), (int) items.stream().filter(ShoppingItemView::purchased).count());
    }

    private static void apply(GatheringShoppingItem it, ShoppingItemRequest req) {
        it.setName(req.name().trim());
        it.setAmount(req.amount());
        it.setUnit(blankToNull(req.unit()));
        it.setBaseServings(req.baseServings());
        it.setForDish(blankToNull(req.forDish()));
        it.setNote(blankToNull(req.note()));
    }

    /** 不同玩法的防重复：主人掌厨同一人不重复点同一道；投票同名菜只能有一条提议；各带一道同名菜只能一人认领。 */
    private void checkDuplicate(Gathering g, List<GatheringWish> all, GatheringParticipant me, String name, Long selfId) {
        String k = key(name);
        for (GatheringWish w : all) {
            if (w.getId().equals(selfId) || !key(w.getDishName()).equals(k)) continue;
            switch (g.getMode()) {
                case HOST_COOK -> {
                    if (w.getParticipantId().equals(me.getId())) throw BizException.conflict("DUPLICATE_WISH", "你已经点过「" + name + "」了");
                }
                case VOTE -> throw BizException.conflict("DUPLICATE_DISH", "已经有人提议「" + name + "」了，直接投一票吧");
                case POTLUCK -> throw BizException.conflict("ALREADY_CLAIMED", "「" + name + "」已经有人认领了，换一道吧");
            }
        }
    }

    private void recountVotes(Long gatheringId) {
        Map<Long, Long> counts = votes.findByGatheringId(gatheringId).stream()
                .collect(Collectors.groupingBy(GatheringVote::getWishId, Collectors.counting()));
        wishes.findByGatheringIdOrderByIdAsc(gatheringId).forEach(w -> w.setVotes(counts.getOrDefault(w.getId(), 0L).intValue()));
    }

    private boolean wishOpen(Gathering g) {
        return g.getStatus() == Status.OPEN && (g.getDeadlineAt() == null || now().isBefore(g.getDeadlineAt()));
    }

    private void requireWishOpen(Gathering g) {
        requireNotClosed(g);
        if (g.getStatus() == Status.CONFIRMED) throw BizException.conflict("GATHERING_CONFIRMED", "菜单已经定了，不能再改");
        if (!wishOpen(g)) throw BizException.conflict("WISH_CLOSED", "已过点菜截止时间");
    }

    private static void requireNotClosed(Gathering g) {
        if (g.isClosed()) throw BizException.conflict("GATHERING_CLOSED", "这场聚餐已经散场了");
    }

    private static void requireMode(Gathering g, Mode mode, String msg) {
        if (g.getMode() != mode) throw BizException.conflict("MODE_MISMATCH", msg);
    }

    private static void requireShopping(Gathering g) {
        if (g.getMode() == Mode.POTLUCK) throw BizException.conflict("MODE_MISMATCH", "各带一道的聚餐不需要统一采购");
    }

    /** hostFamilyId 只能填自己所在的家庭（仅用于日历占位）。 */
    private void checkHostFamily(Long familyId) {
        if (familyId == null) return;
        if (familyMembers.findByFamilyIdAndUserId(familyId, CurrentUser.id()).isEmpty()) {
            throw BizException.forbidden("只能在自己所在的家庭日历上占位");
        }
    }

    private Gathering byToken(String token) {
        if (token == null || token.isBlank() || token.length() > 32) throw BizException.notFound("邀请");
        return gatherings.findByInviteToken(token.trim()).orElseThrow(() -> BizException.notFound("邀请"));
    }

    private GatheringWish wish(Long gid, Long wishId) {
        return wishes.findByIdAndGatheringId(wishId, gid).orElseThrow(() -> BizException.notFound("菜"));
    }

    private GatheringShoppingItem shoppingItem(Long gid, Long itemId) {
        return shoppingItems.findByIdAndGatheringId(itemId, gid).orElseThrow(() -> BizException.notFound("采购项"));
    }

    private AppUser currentUser() {
        return users.findById(CurrentUser.id()).orElseThrow(() -> BizException.unauthorized("用户不存在"));
    }

    private Map<Long, String> hostNames(List<Gathering> list) {
        Set<Long> ids = list.stream().map(Gathering::getHostUserId).collect(Collectors.toSet());
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getNickname));
    }

    private String newToken() {
        for (int i = 0; i < 5; i++) {
            byte[] b = new byte[18];
            RANDOM.nextBytes(b);
            String t = Base64.getUrlEncoder().withoutPadding().encodeToString(b); // 24 位
            if (!gatherings.existsByInviteToken(t)) return t;
        }
        throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "TOKEN_FAILED", "生成邀请失败，请重试");
    }

    static int headcount(List<GatheringParticipant> ps) {
        return ps.stream().filter(p -> p.getRsvp() == Rsvp.YES).mapToInt(GatheringParticipant::getHeadcount).sum();
    }

    private static int count(List<GatheringParticipant> ps, Rsvp r) {
        return (int) ps.stream().filter(p -> p.getRsvp() == r).count();
    }

    private static List<Long> yesUserIds(List<GatheringParticipant> ps) {
        return ps.stream().filter(p -> p.getRsvp() == Rsvp.YES && p.getUserId() != null)
                .map(GatheringParticipant::getUserId).toList();
    }

    private static String key(String name) {
        return name.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String slotLabel(Gathering g) {
        return g.getMealDate().format(DateTimeFormatter.ofPattern("M月d日")) + MEAL_NAMES.get(g.getMealType());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
