package com.zzwen6.familymeal.rotation;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.family.FamilyMemberRepository;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.menu.MenuSlots;
import com.zzwen6.familymeal.rotation.CookRotationDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CookRotationService {
    private final CookRotationRepository rotations;
    private final CookAssignmentRepository assignments;
    private final FamilyAccess access;
    private final FamilyMemberRepository members;
    private final MenuRepository menus;
    private final MenuSlots slots;
    private final KitchenDuty duty;

    public CookRotationService(CookRotationRepository rotations, CookAssignmentRepository assignments,
                               FamilyAccess access, FamilyMemberRepository members, MenuRepository menus,
                               MenuSlots slots, KitchenDuty duty) {
        this.rotations = rotations;
        this.assignments = assignments;
        this.access = access;
        this.members = members;
        this.menus = menus;
        this.slots = slots;
        this.duty = duty;
    }

    @Transactional(readOnly = true)
    public RotationView get(Long familyId) {
        access.member(familyId);
        return view(rotations.findByFamilyId(familyId).orElse(null), familyId);
    }

    @Transactional
    public RotationView update(Long familyId, UpdateRotationRequest req) {
        access.admin(familyId);
        CookRotation r = rotations.findByFamilyId(familyId).orElseGet(() -> {
            CookRotation n = new CookRotation();
            n.setFamilyId(familyId);
            n.setEnabled(false);
            n.setMealTypesRaw("DINNER");
            return n;
        });
        if (req.enabled() != null) r.setEnabled(req.enabled());
        if (req.mealTypes() != null) {
            if (req.mealTypes().isEmpty()) throw BizException.badRequest("至少选择一个适用餐次");
            r.setMealTypeList(req.mealTypes());
        }
        if (req.memberIds() != null) {
            Map<Long, FamilyMember> inFamily = members.findByFamilyIdOrderByIdAsc(familyId).stream()
                    .collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
            r.getMembers().clear();
            int order = 0;
            Set<Long> seen = new HashSet<>();
            for (Long mid : req.memberIds()) {
                if (mid == null || !seen.add(mid)) continue;
                if (!inFamily.containsKey(mid)) throw BizException.notFound("成员");
                CookRotationMember rm = new CookRotationMember();
                rm.setRotation(r);
                rm.setMemberId(mid);
                rm.setSortOrder(order++);
                r.getMembers().add(rm);
            }
        }
        rotations.save(r);
        return view(r, familyId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> list(Long familyId, LocalDate from, LocalDate to) {
        access.member(familyId);
        if (from == null) from = slots.today();
        if (to == null) to = from.plusDays(6);
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) throw BizException.badRequest("日期范围不正确（最多 62 天）");
        return toViews(familyId, from, to, assignments.findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(
                familyId, from, to));
    }

    @Transactional
    public AssignmentView assign(Long familyId, AssignRequest req) {
        FamilyMember me = access.member(familyId);
        duty.requireManage(me, req.mealDate(), req.mealType());
        if (req.mealDate().isBefore(slots.today())) throw BizException.badRequest("不能给过去的日期排班");
        Optional<CookAssignment> existing = assignments.findByFamilyIdAndMealDateAndMealType(
                familyId, req.mealDate(), req.mealType());
        if (req.memberId() == null) {
            existing.ifPresent(assignments::delete);
            return new AssignmentView(req.mealDate(), req.mealType(), null, null, null, null, null, false);
        }
        FamilyMember target = members.findByIdAndFamilyId(req.memberId(), familyId)
                .orElseThrow(() -> BizException.notFound("成员"));
        CookAssignment a = existing.orElseGet(CookAssignment::new);
        a.setFamilyId(familyId);
        a.setMealDate(req.mealDate());
        a.setMealType(req.mealType());
        a.setMemberId(target.getId());
        a.setSource(CookAssignment.Source.MANUAL);
        assignments.save(a);
        return toViews(familyId, req.mealDate(), req.mealDate(), List.of(a)).getFirst();
    }

    @Transactional
    public List<AssignmentView> swap(Long familyId, SwapRequest req) {
        FamilyMember me = access.member(familyId);
        if (req.date1().isBefore(slots.today()) || req.date2().isBefore(slots.today())) {
            throw BizException.badRequest("不能对调过去的日期");
        }
        boolean can = duty.canManageAssignment(me, req.date1(), req.mealType1())
                || duty.canManageAssignment(me, req.date2(), req.mealType2());
        if (!can) throw BizException.forbidden("只有管理员或本餐指定掌厨可以对调");
        CookAssignment a1 = assignments.findByFamilyIdAndMealDateAndMealType(familyId, req.date1(), req.mealType1())
                .orElse(null);
        CookAssignment a2 = assignments.findByFamilyIdAndMealDateAndMealType(familyId, req.date2(), req.mealType2())
                .orElse(null);
        if (a1 == null && a2 == null) throw BizException.badRequest("这两餐都还没有指定掌厨");
        Long m1 = a1 == null ? null : a1.getMemberId();
        Long m2 = a2 == null ? null : a2.getMemberId();
        if (Objects.equals(m1, m2) && req.date1().equals(req.date2()) && req.mealType1() == req.mealType2()) {
            LocalDate from = req.date1().isBefore(req.date2()) ? req.date1() : req.date2();
            LocalDate to = req.date1().isAfter(req.date2()) ? req.date1() : req.date2();
            return list(familyId, from, to);
        }
        upsertManual(familyId, req.date1(), req.mealType1(), m2, a1);
        upsertManual(familyId, req.date2(), req.mealType2(), m1, a2);
        LocalDate from = req.date1().isBefore(req.date2()) ? req.date1() : req.date2();
        LocalDate to = req.date1().isAfter(req.date2()) ? req.date1() : req.date2();
        return list(familyId, from, to);
    }

    /**
     * 按「谁掌厨次数更少、更久没做，次数相同时按轮换顺序」自动填空档。
     * 不覆盖手工指定，也不改已经确认/开饭的餐次安排。
     */
    @Transactional
    public List<AssignmentView> generate(Long familyId, GenerateRequest req) {
        FamilyMember me = access.member(familyId);
        if (!me.getRole().isAdmin() && !me.getRole().canCook()) {
            throw BizException.forbidden("只有管理员或掌厨可以生成安排");
        }
        CookRotation rotation = rotations.findByFamilyId(familyId)
                .orElseThrow(() -> BizException.badRequest("请先设置参与轮换的家人和适用餐次"));
        List<CookRotationMember> roster = rotation.getMembers();
        if (roster.isEmpty()) throw BizException.badRequest("请先设置参与轮换的家人");
        List<MealType> types = rotation.mealTypeList();
        LocalDate from = req != null && req.from() != null ? req.from() : slots.today();
        LocalDate to;
        if (req != null && req.to() != null) to = req.to();
        else {
            int days = req != null && req.days() != null ? req.days() : 7;
            if (days < 1 || days > 31) throw BizException.badRequest("生成天数应在 1～31 之间");
            to = from.plusDays(days - 1L);
        }
        if (from.isBefore(slots.today())) from = slots.today();
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) throw BizException.badRequest("日期范围不正确（最多 62 天）");

        Map<Long, Stat> stats = historyStats(familyId, from.minusDays(60), from.minusDays(1));
        Map<String, Menu> menuMap = menus.findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(familyId, from, to)
                .stream().collect(Collectors.toMap(m -> key(m.getMealDate(), m.getMealType()), Function.identity()));
        List<CookAssignment> existing = assignments.findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(
                familyId, from, to);
        Map<String, CookAssignment> existMap = existing.stream()
                .collect(Collectors.toMap(a -> key(a.getMealDate(), a.getMealType()), Function.identity()));

        // 范围内要保留的安排计入统计，再填空档
        for (CookAssignment a : existing) {
            Menu menu = menuMap.get(key(a.getMealDate(), a.getMealType()));
            boolean keep = a.getSource() == CookAssignment.Source.MANUAL || isSealed(menu);
            if (keep) bump(stats, a.getMemberId(), a.getMealDate());
        }

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (MealType type : types) {
                String k = key(d, type);
                CookAssignment cur = existMap.get(k);
                Menu menu = menuMap.get(k);
                if (cur != null && (cur.getSource() == CookAssignment.Source.MANUAL || isSealed(menu))) continue;
                Long pick = pickNext(roster, stats);
                if (cur == null) {
                    cur = new CookAssignment();
                    cur.setFamilyId(familyId);
                    cur.setMealDate(d);
                    cur.setMealType(type);
                }
                cur.setMemberId(pick);
                cur.setSource(CookAssignment.Source.AUTO);
                assignments.save(cur);
                bump(stats, pick, d);
            }
        }
        return list(familyId, from, to);
    }

    private Map<Long, Stat> historyStats(Long familyId, LocalDate from, LocalDate to) {
        Map<Long, Stat> stats = new HashMap<>();
        if (to.isBefore(from)) return stats;
        for (Menu m : menus.findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(familyId, from, to)) {
            if (!isSealed(m) || m.getConfirmedBy() == null) continue;
            bump(stats, m.getConfirmedBy(), m.getMealDate());
        }
        return stats;
    }

    private static Long pickNext(List<CookRotationMember> roster, Map<Long, Stat> stats) {
        return roster.stream()
                .min(Comparator
                        .comparingInt((CookRotationMember rm) -> stats.getOrDefault(rm.getMemberId(), Stat.EMPTY).count)
                        .thenComparing((CookRotationMember rm) -> {
                            LocalDate last = stats.getOrDefault(rm.getMemberId(), Stat.EMPTY).last;
                            return last == null ? LocalDate.MIN : last;
                        })
                        .thenComparingInt(CookRotationMember::getSortOrder))
                .map(CookRotationMember::getMemberId)
                .orElseThrow();
    }

    private static void bump(Map<Long, Stat> stats, Long memberId, LocalDate date) {
        Stat s = stats.computeIfAbsent(memberId, k -> new Stat());
        s.count++;
        if (s.last == null || date.isAfter(s.last)) s.last = date;
    }

    private static boolean isSealed(Menu menu) {
        return menu != null && (menu.getStatus() == MenuStatus.CONFIRMED || menu.getStatus() == MenuStatus.READY);
    }

    private void upsertManual(Long familyId, LocalDate date, MealType type, Long memberId, CookAssignment existing) {
        if (memberId == null) {
            if (existing != null) assignments.delete(existing);
            return;
        }
        CookAssignment a = existing == null ? new CookAssignment() : existing;
        a.setFamilyId(familyId);
        a.setMealDate(date);
        a.setMealType(type);
        a.setMemberId(memberId);
        a.setSource(CookAssignment.Source.MANUAL);
        assignments.save(a);
    }

    private RotationView view(CookRotation r, Long familyId) {
        if (r == null) return new RotationView(false, List.of(MealType.DINNER), List.of());
        Map<Long, FamilyMember> map = members.findByFamilyIdOrderByIdAsc(familyId).stream()
                .collect(Collectors.toMap(FamilyMember::getId, Function.identity()));
        List<RotationMemberView> list = r.getMembers().stream()
                .map(m -> RotationMemberView.of(m, map.get(m.getMemberId())))
                .toList();
        return new RotationView(r.isEnabled(), r.mealTypeList(), list);
    }

    private List<AssignmentView> toViews(Long familyId, LocalDate from, LocalDate to, List<CookAssignment> list) {
        Map<Long, String> names = members.findByFamilyIdOrderByIdAsc(familyId).stream()
                .collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));
        Map<String, Menu> menuMap = menus.findByFamilyIdAndMealDateBetweenOrderByMealDateAscMealTypeAsc(familyId, from, to)
                .stream().collect(Collectors.toMap(m -> key(m.getMealDate(), m.getMealType()), Function.identity()));
        return list.stream().map(a -> {
            Menu menu = menuMap.get(key(a.getMealDate(), a.getMealType()));
            Long confirmedBy = menu == null ? null : menu.getConfirmedBy();
            String confirmedName = confirmedBy == null ? null : names.get(confirmedBy);
            boolean proxy = confirmedBy != null && !confirmedBy.equals(a.getMemberId());
            return new AssignmentView(a.getMealDate(), a.getMealType(), a.getMemberId(),
                    names.getOrDefault(a.getMemberId(), "已退出成员"), a.getSource(),
                    confirmedBy, confirmedName, proxy);
        }).toList();
    }

    private static String key(LocalDate d, MealType t) {
        return d + "|" + t;
    }

    private static final class Stat {
        static final Stat EMPTY = new Stat();
        int count;
        LocalDate last;
    }
}
