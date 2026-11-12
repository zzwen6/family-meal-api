package com.zzwen6.familymeal.family;

import com.zzwen6.familymeal.auth.AppUser;
import com.zzwen6.familymeal.auth.AppUserRepository;
import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.Enums.Role;
import com.zzwen6.familymeal.family.FamilyDtos.*;
import com.zzwen6.familymeal.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FamilyService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyRepository families;
    private final FamilyMemberRepository members;
    private final AppUserRepository users;
    private final FamilyAccess access;
    private final PasswordEncoder encoder;

    public FamilyService(FamilyRepository families, FamilyMemberRepository members, AppUserRepository users,
                         FamilyAccess access, PasswordEncoder encoder) {
        this.families = families;
        this.members = members;
        this.users = users;
        this.access = access;
        this.encoder = encoder;
    }

    @Transactional
    public FamilyView create(CreateFamilyRequest req) {
        access.requireCanHost();
        AppUser me = users.findById(CurrentUser.id()).orElseThrow(() -> BizException.unauthorized("用户不存在"));
        return createFor(me, req.name(), req.displayName());
    }

    /** 创建家庭并把 owner 设为管理员。也被首次启动的管理员初始化复用。 */
    @Transactional
    public FamilyView createFor(AppUser owner, String name, String displayName) {
        Family f = new Family();
        f.setName(name.trim());
        f.setInviteCode(newInviteCode());
        f.setCreatedBy(owner.getId());
        families.save(f);
        FamilyMember m = new FamilyMember();
        m.setFamilyId(f.getId());
        m.setUserId(owner.getId());
        m.setRole(Role.ADMIN);
        m.setDisplayName(blankToDefault(displayName, owner.getNickname()));
        members.save(m);
        return FamilyView.of(f, m, 1);
    }

    @Transactional
    public FamilyView join(JoinFamilyRequest req) {
        AppUser me = users.findById(CurrentUser.id()).orElseThrow(() -> BizException.unauthorized("用户不存在"));
        return joinFor(me, req.inviteCode(), req.displayName());
    }

    @Transactional
    public FamilyView joinFor(AppUser user, String inviteCode, String displayName) {
        Family f = families.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> BizException.badRequest("邀请码无效"));
        FamilyMember m = members.findByFamilyIdAndUserId(f.getId(), user.getId()).orElseGet(() -> {
            FamilyMember nm = new FamilyMember();
            nm.setFamilyId(f.getId());
            nm.setUserId(user.getId());
            nm.setRole(Role.MEMBER);
            nm.setDisplayName(blankToDefault(displayName, user.getNickname()));
            return members.save(nm);
        });
        return FamilyView.of(f, m, members.findByFamilyIdOrderByIdAsc(f.getId()).size());
    }

    @Transactional(readOnly = true)
    public List<FamilyView> mine() {
        return mineFor(CurrentUser.id());
    }

    @Transactional(readOnly = true)
    public List<FamilyView> mineFor(Long userId) {
        return members.findByUserIdOrderByIdAsc(userId).stream().map(m -> {
            Family f = families.findById(m.getFamilyId()).orElseThrow();
            return FamilyView.of(f, m, members.findByFamilyIdOrderByIdAsc(f.getId()).size());
        }).toList();
    }

    @Transactional(readOnly = true)
    public FamilyView get(Long familyId) {
        FamilyMember me = access.member(familyId);
        Family f = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        return FamilyView.of(f, me, members.findByFamilyIdOrderByIdAsc(familyId).size());
    }

    @Transactional
    public FamilyView update(Long familyId, UpdateFamilyRequest req) {
        FamilyMember me = access.admin(familyId);
        Family f = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        if (req.name() != null && !req.name().isBlank()) f.setName(req.name().trim());
        if (req.breakfastDeadline() != null) f.setBreakfastDeadline(req.breakfastDeadline());
        if (req.lunchDeadline() != null) f.setLunchDeadline(req.lunchDeadline());
        if (req.dinnerDeadline() != null) f.setDinnerDeadline(req.dinnerDeadline());
        if (req.fatigueWindowDays() != null) {
            int d = req.fatigueWindowDays();
            if (d < 1 || d > 60) throw BizException.badRequest("吃腻统计窗口应在 1～60 天之间");
            f.setFatigueWindowDays(d);
        }
        if (req.fatigueThreshold() != null) {
            int th = req.fatigueThreshold();
            if (th < 1 || th > 20) throw BizException.badRequest("吃腻次数阈值应在 1～20 之间");
            f.setFatigueThreshold(th);
        }
        return FamilyView.of(f, me, members.findByFamilyIdOrderByIdAsc(familyId).size());
    }

    @Transactional
    public FamilyView refreshInviteCode(Long familyId) {
        FamilyMember me = access.admin(familyId);
        Family f = families.findById(familyId).orElseThrow(() -> BizException.notFound("家庭"));
        f.setInviteCode(newInviteCode());
        return FamilyView.of(f, me, members.findByFamilyIdOrderByIdAsc(familyId).size());
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(Long familyId) {
        access.member(familyId);
        List<FamilyMember> list = members.findByFamilyIdOrderByIdAsc(familyId);
        Map<Long, AppUser> userMap = users.findAllById(list.stream().map(FamilyMember::getUserId).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<Long, String> names = list.stream().collect(Collectors.toMap(FamilyMember::getId, FamilyMember::getDisplayName));
        return list.stream().map(m -> MemberView.of(m, userMap.get(m.getUserId()),
                m.getGuardianMemberId() == null ? null : names.get(m.getGuardianMemberId()))).toList();
    }

    @Transactional
    public MemberView createMember(Long familyId, CreateMemberRequest req) {
        FamilyMember admin = access.admin(familyId);
        String username = req.username().trim();
        if (users.existsByUsername(username)) throw BizException.conflict("USERNAME_TAKEN", "用户名已被使用");
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setNickname(req.displayName().trim());
        users.save(u);
        FamilyMember m = new FamilyMember();
        m.setFamilyId(familyId);
        m.setUserId(u.getId());
        Role role = req.role() == null ? Role.MEMBER : req.role();
        m.setRole(role);
        m.setDisplayName(req.displayName().trim());
        m.setDietaryNotes(req.dietaryNotes());
        m.setAllergies(req.allergies());
        if (role == Role.CHILD) {
            m.setGuardianMemberId(resolveGuardian(familyId, req.guardianMemberId(), admin.getId()));
        } else if (req.guardianMemberId() != null) {
            throw BizException.badRequest("只有儿童角色才能指定监护人");
        }
        members.save(m);
        return toView(familyId, m, u);
    }

    @Transactional
    public MemberView updateMember(Long familyId, Long memberId, UpdateMemberRequest req) {
        FamilyMember me = access.member(familyId);
        FamilyMember target = members.findByIdAndFamilyId(memberId, familyId)
                .orElseThrow(() -> BizException.notFound("成员"));
        boolean self = target.getId().equals(me.getId());
        if (!self && !me.getRole().isAdmin()) throw BizException.forbidden("只能修改自己的资料");
        if (req.role() != null && req.role() != target.getRole()) {
            if (!me.getRole().isAdmin()) throw BizException.forbidden("只有管理员可以调整角色");
            if (target.getRole() == Role.ADMIN && members.countByFamilyIdAndRole(familyId, Role.ADMIN) <= 1) {
                throw BizException.badRequest("家庭至少要保留一位管理员");
            }
            target.setRole(req.role());
        }
        if (req.displayName() != null && !req.displayName().isBlank()) target.setDisplayName(req.displayName().trim());
        if (req.dietaryNotes() != null) target.setDietaryNotes(req.dietaryNotes());
        if (req.allergies() != null) target.setAllergies(req.allergies());
        if (req.guardianMemberId() != null || (target.getRole() == Role.CHILD && target.getGuardianMemberId() == null)) {
            if (target.getRole() != Role.CHILD) throw BizException.badRequest("只有儿童角色才能指定监护人");
            if (req.guardianMemberId() != null && !me.getRole().isAdmin()) {
                throw BizException.forbidden("只有管理员可以指定监护人");
            }
            Long requested = req.guardianMemberId() != null ? req.guardianMemberId()
                    : (target.getGuardianMemberId() != null ? target.getGuardianMemberId() : me.getId());
            target.setGuardianMemberId(resolveGuardian(familyId, requested, me.getId()));
        }
        if (target.getRole() != Role.CHILD) {
            target.setGuardianMemberId(null);
        } else if (target.getGuardianMemberId() == null) {
            target.setGuardianMemberId(resolveGuardian(familyId, null, me.getId()));
        }
        if (req.password() != null && !req.password().isBlank()) {
            if (!me.getRole().isAdmin()) throw BizException.forbidden("只有管理员可以重设密码");
            AppUser u = users.findById(target.getUserId()).orElseThrow(() -> BizException.notFound("用户"));
            if (u.getUsername() == null) throw BizException.badRequest("该账号没有用户名，无法设置密码");
            u.setPasswordHash(encoder.encode(req.password()));
        }
        return toView(familyId, target, users.findById(target.getUserId()).orElse(null));
    }

    private MemberView toView(Long familyId, FamilyMember m, AppUser u) {
        String gname = null;
        if (m.getGuardianMemberId() != null) {
            gname = members.findByIdAndFamilyId(m.getGuardianMemberId(), familyId)
                    .map(FamilyMember::getDisplayName).orElse(null);
        }
        return MemberView.of(m, u, gname);
    }

    /** 监护人必须是同一家庭的非儿童成员；未指定时使用 fallback（通常是当前管理员）。 */
    private Long resolveGuardian(Long familyId, Long requested, Long fallback) {
        Long id = requested != null ? requested : fallback;
        FamilyMember g = members.findByIdAndFamilyId(id, familyId).orElseThrow(() -> BizException.notFound("监护人"));
        if (g.getRole() == Role.CHILD) throw BizException.badRequest("监护人不能是儿童");
        return g.getId();
    }

    @Transactional
    public void removeMember(Long familyId, Long memberId) {
        FamilyMember me = access.member(familyId);
        FamilyMember target = members.findByIdAndFamilyId(memberId, familyId)
                .orElseThrow(() -> BizException.notFound("成员"));
        boolean self = target.getId().equals(me.getId());
        if (!self && !me.getRole().isAdmin()) throw BizException.forbidden("只有管理员可以移除成员");
        if (target.getRole() == Role.ADMIN && members.countByFamilyIdAndRole(familyId, Role.ADMIN) <= 1) {
            throw BizException.badRequest("家庭至少要保留一位管理员");
        }
        members.delete(target);
    }

    private String newInviteCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            if (!families.existsByInviteCode(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("无法生成邀请码");
    }

    private static String blankToDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }
}
