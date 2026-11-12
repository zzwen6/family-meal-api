package com.zzwen6.familymeal.rating;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.MenuStatus;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.family.FamilyMemberRepository;
import com.zzwen6.familymeal.menu.Menu;
import com.zzwen6.familymeal.menu.MenuRepository;
import com.zzwen6.familymeal.rating.RatingDtos.RatingRequest;
import com.zzwen6.familymeal.rating.RatingDtos.RatingView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MealRatingService {
    private final MealRatingRepository ratings;
    private final FamilyAccess access;
    private final FamilyMemberRepository members;
    private final MenuRepository menus;

    public MealRatingService(MealRatingRepository ratings, FamilyAccess access, FamilyMemberRepository members,
                             MenuRepository menus) {
        this.ratings = ratings;
        this.access = access;
        this.members = members;
        this.menus = menus;
    }

    @Transactional(readOnly = true)
    public List<RatingView> list(Long familyId, Long menuId) {
        FamilyMember me = access.member(familyId);
        menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
        return toViews(familyId, ratings.findByFamilyIdAndMenuIdOrderByIdAsc(familyId, menuId), me);
    }

    @Transactional
    public RatingView upsert(Long familyId, Long menuId, RatingRequest req) {
        FamilyMember me = access.member(familyId);
        Menu menu = menus.findByIdAndFamilyId(menuId, familyId).orElseThrow(() -> BizException.notFound("菜单"));
        if (menu.getStatus() != MenuStatus.READY) {
            throw new BizException(HttpStatus.CONFLICT, "MENU_NOT_READY", "这餐还没开饭，开饭后再评价");
        }
        FamilyMember target = me;
        if (req.memberId() != null && !req.memberId().equals(me.getId())) {
            target = members.findByIdAndFamilyId(req.memberId(), familyId).orElseThrow(() -> BizException.notFound("成员"));
            access.requireActFor(me, target);
        }
        MealRating r = ratings.findByMenuIdAndMemberId(menuId, target.getId()).orElseGet(MealRating::new);
        r.setFamilyId(familyId);
        r.setMenuId(menuId);
        r.setMemberId(target.getId());
        r.setScore(req.score());
        r.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
        ratings.save(r);
        return toViews(familyId, List.of(r), me).getFirst();
    }

    public List<RatingView> toViews(Long familyId, List<MealRating> list, FamilyMember me) {
        if (list.isEmpty()) return List.of();
        Map<Long, String> names = members.findByFamilyIdOrderByIdAsc(familyId).stream()
                .collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));
        Map<Long, FamilyMember> memberMap = members.findByFamilyIdOrderByIdAsc(familyId).stream()
                .collect(Collectors.toMap(FamilyMember::getId, m -> m));
        return list.stream().map(r -> {
            FamilyMember owner = memberMap.get(r.getMemberId());
            boolean editable = owner != null && access.canActFor(me, owner);
            return new RatingView(r.getId(), r.getMenuId(), r.getMemberId(),
                    names.getOrDefault(r.getMemberId(), "已退出成员"),
                    r.getScore(), r.getComment(), editable, r.getUpdatedAt());
        }).toList();
    }
}
