package com.zzwen6.familymeal.notify;

import com.zzwen6.familymeal.auth.AppUser;
import com.zzwen6.familymeal.auth.AppUserRepository;
import com.zzwen6.familymeal.common.Enums.NotificationType;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.family.FamilyMember;
import com.zzwen6.familymeal.family.FamilyMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {
    public record NotificationView(Long id, Long menuId, NotificationType type, String title, String content,
                                   String wechatStatus, LocalDateTime createdAt) {}

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

    @Transactional
    public Notification publish(Long familyId, Long menuId, NotificationType type, String title, String content,
                                Long createdBy) {
        List<Long> userIds = members.findByFamilyIdOrderByIdAsc(familyId).stream().map(FamilyMember::getUserId).toList();
        List<String> openids = users.findAllById(userIds).stream().map(AppUser::getWxOpenid)
                .filter(Objects::nonNull).toList();
        Notification n = new Notification();
        n.setFamilyId(familyId);
        n.setMenuId(menuId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setCreatedBy(createdBy);
        n.setWechatStatus(wechat.send(openids, title, content));
        return repo.save(n);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> recent(Long familyId, int limit) {
        access.member(familyId);
        return repo.findByFamilyIdOrderByIdDesc(familyId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .stream().map(NotificationService::view).toList();
    }

    public static NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getMenuId(), n.getType(), n.getTitle(), n.getContent(),
                n.getWechatStatus().name(), n.getCreatedAt());
    }
}
