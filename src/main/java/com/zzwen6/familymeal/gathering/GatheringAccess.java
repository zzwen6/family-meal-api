package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 聚餐权限入口：所有 /api/gatherings/{id}/** 先通过这里拿到「我在这场里的参与身份」。
 * 不是参与者一律 403（不区分聚餐是否存在，避免枚举）；管理操作再要求是发起人。
 * 这里完全不看家庭成员关系：家庭成员不因此能看聚餐，参与者也不因此能看家庭。
 */
@Component
public class GatheringAccess {
    public record Ctx(Gathering gathering, GatheringParticipant me) {
        public boolean isHost() { return gathering.getHostUserId().equals(me.getUserId()); }
    }

    private final GatheringRepository gatherings;
    private final GatheringParticipantRepository participants;

    public GatheringAccess(GatheringRepository gatherings, GatheringParticipantRepository participants) {
        this.gatherings = gatherings;
        this.participants = participants;
    }

    public Ctx participant(Long gatheringId) {
        Long uid = CurrentUser.id();
        Gathering g = gatherings.findById(gatheringId).orElse(null);
        GatheringParticipant p = g == null ? null : participants.findByGatheringIdAndUserId(gatheringId, uid).orElse(null);
        if (p == null) throw BizException.forbidden("你不是这场聚餐的参与者");
        return new Ctx(g, p);
    }

    public Ctx host(Long gatheringId) {
        Ctx c = participant(gatheringId);
        if (!c.isHost()) throw BizException.forbidden("只有发起人可以这样做");
        return c;
    }
}
