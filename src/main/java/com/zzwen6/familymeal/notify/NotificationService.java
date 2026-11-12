package com.zzwen6.familymeal.notify;

import com.zzwen6.familymeal.auth.AppUser;
import com.zzwen6.familymeal.auth.AppUserRepository;
import com.zzwen6.familymeal.common.Enums.NotificationType;
import com.zzwen6.familymeal.common.Enums.WechatStatus;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.family.FamilyMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 站内通知 + 微信订阅消息。家庭通知按 family_id 存、聚餐通知按 gathering_id 存，两边互不可见。
 */
@Service
public class NotificationService {
    public record NotificationView(Long id, Long menuId, Long gatheringId, NotificationType type, String title,
                                   String content, String wechatStatus, LocalDateTime createdAt) {}

    /** 小程序里点订阅消息打开的页面。 */
    static final String FAMILY_PAGE = "pages/index/index";

    private final NotificationRepository repo;
    private final FamilyMemberRepository members;
    private final AppUserRepository users;
    private final WechatNotifier wechat;
    private final FamilyAccess access;

    public NotificationService(NotificationRepository repo, FamilyMemberRepository members, AppUserRepository users,
                               WechatNotifier wechat, FamilyAccess access) {
        this.repo = repo;
        this.members = members;
        this.users = users;
        this.wechat = wechat;
        this.access = access;
    }

    /** 家庭通知：发给全家。 */
    @Transactional
    public Notification publish(Long familyId, Long menuId, NotificationType type, String title, String content,
                                Long createdBy) {
        List<Long> userIds = members.findByFamilyIdOrderByIdAsc(familyId).stream().map(FamilyMember::getUserId).toList();
        Notification n = new Notification();
        n.setFamilyId(familyId);
        n.setMenuId(menuId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setCreatedBy(createdBy);
        n.setWechatStatus(push(type, userIds, title, content, FAMILY_PAGE));
        return repo.save(n);
    }

    /** 聚餐通知：只写到这场聚餐下，微信推送只发给传进来的参与者（通常是 RSVP=YES 的人）。 */
    @Transactional
    public Notification publishGathering(Long gatheringId, Collection<Long> recipientUserIds, NotificationType type,
                                         String title, String content, Long createdBy) {
        Notification n = new Notification();
        n.setGatheringId(gatheringId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setCreatedBy(createdBy);
        n.setWechatStatus(push(type, recipientUserIds, title, content, "pages/gathering/detail?id=" + gatheringId));
        return repo.save(n);
    }

    private WechatStatus push(NotificationType type, Collection<Long> userIds, String title, String content, String page) {
        if (!type.pushesWechat()) return WechatStatus.SKIPPED;
        List<String> openids = users.findAllById(userIds).stream().map(AppUser::getWxOpenid)
                .filter(Objects::nonNull).toList();
        return wechat.send(openids, title, content, page);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> recent(Long familyId, int limit) {
        access.member(familyId);
        return repo.findByFamilyIdOrderByIdDesc(familyId, page(limit)).stream().map(NotificationService::view).toList();
    }

    /** 调用方（GatheringService）负责先校验是本场参与者。 */
    @Transactional(readOnly = true)
    public List<NotificationView> recentForGathering(Long gatheringId, int limit) {
        return repo.findByGatheringIdOrderByIdDesc(gatheringId, page(limit)).stream().map(NotificationService::view).toList();
    }

    private static PageRequest page(int limit) {
        return PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
    }

    public static NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getMenuId(), n.getGatheringId(), n.getType(), n.getTitle(),
                n.getContent(), n.getWechatStatus().name(), n.getCreatedAt());
    }
}
