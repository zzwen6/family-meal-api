package com.zzwen6.familymeal.family;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.Role;
import com.zzwen6.familymeal.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 租户隔离入口：所有 /api/families/{familyId}/** 接口先通过这里拿到「我在这个家里的成员身份」，
 * 再按角色判断能否写。不是该家庭成员一律 403（不区分家庭是否存在，避免枚举）。
 */
@Component
public class FamilyAccess {
    private final FamilyMemberRepository members;

    public FamilyAccess(FamilyMemberRepository members) {
        this.members = members;
    }

    public FamilyMember member(Long familyId) {
        return members.findByFamilyIdAndUserId(familyId, CurrentUser.id())
                .orElseThrow(() -> BizException.forbidden("你不是这个家庭的成员"));
    }

    public FamilyMember admin(Long familyId) {
        FamilyMember m = member(familyId);
        if (!m.getRole().isAdmin()) throw BizException.forbidden("只有家庭管理员可以这样做");
        return m;
    }

    public FamilyMember cook(Long familyId) {
        FamilyMember m = member(familyId);
        if (m.getRole() == Role.CHILD) throw BizException.forbidden("小朋友不能改这个");
        if (!m.getRole().canCook()) throw BizException.forbidden("只有掌厨或管理员可以这样做");
        return m;
    }

    public void requireNotChild(FamilyMember m) {
        if (m.getRole() == Role.CHILD) throw BizException.forbidden("小朋友不能做这件事");
    }

    /**
     * 账号在所有家庭都是儿童时，不能创建家庭或发起聚餐（避免孩子给自己再建一个家当管理员）。
     */
    public void requireCanHost() {
        List<FamilyMember> mine = members.findByUserIdOrderByIdAsc(CurrentUser.id());
        if (!mine.isEmpty() && mine.stream().allMatch(m -> m.getRole() == Role.CHILD)) {
            throw BizException.forbidden("小朋友不能做这件事");
        }
    }

    /** 监护人必须是同一家庭的非儿童成员。 */
    public boolean isGuardianOf(FamilyMember actor, FamilyMember target) {
        return target.getRole() == Role.CHILD
                && target.getGuardianMemberId() != null
                && target.getGuardianMemberId().equals(actor.getId())
                && actor.getFamilyId().equals(target.getFamilyId())
                && actor.getRole() != Role.CHILD;
    }

    /** 本人、掌厨/管理员，或该孩子的监护人（即使角色是 MEMBER）可以代操作。 */
    public boolean canActFor(FamilyMember actor, FamilyMember target) {
        if (actor.getId().equals(target.getId())) return true;
        if (actor.getRole() == Role.CHILD) return false;
        if (actor.getRole().canCook()) return true;
        return isGuardianOf(actor, target);
    }

    public void requireActFor(FamilyMember actor, FamilyMember target) {
        if (!canActFor(actor, target)) {
            throw BizException.forbidden("只能替自己点餐，或作为监护人替孩子点");
        }
    }
}
