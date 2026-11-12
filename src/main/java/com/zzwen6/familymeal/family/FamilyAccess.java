package com.zzwen6.familymeal.family;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.security.CurrentUser;
import org.springframework.stereotype.Component;

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
        if (!m.getRole().canCook()) throw BizException.forbidden("只有掌厨或管理员可以这样做");
        return m;
    }
}
