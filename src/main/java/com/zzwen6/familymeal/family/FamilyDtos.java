package com.zzwen6.familymeal.family;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zzwen6.familymeal.auth.AppUser;
import com.zzwen6.familymeal.common.Enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class FamilyDtos {
    private FamilyDtos() {}

    public record CreateFamilyRequest(
            @NotBlank(message = "家庭名称不能为空") @Size(max = 64) String name,
            @Size(max = 64) String displayName) {}

    public record JoinFamilyRequest(
            @NotBlank(message = "邀请码不能为空") @Size(max = 16) String inviteCode,
            @Size(max = 64) String displayName) {}

    /** 家庭规则：各餐截止时间（HH:mm）。早餐截止按前一天计算。 */
    public record UpdateFamilyRequest(
            @Size(max = 64) String name,
            @JsonFormat(pattern = "HH:mm") LocalTime breakfastDeadline,
            @JsonFormat(pattern = "HH:mm") LocalTime lunchDeadline,
            @JsonFormat(pattern = "HH:mm") LocalTime dinnerDeadline,
            Integer fatigueWindowDays,
            Integer fatigueThreshold) {}

    public record FamilyView(Long id, String name, String inviteCode,
                             @JsonFormat(pattern = "HH:mm") LocalTime breakfastDeadline,
                             @JsonFormat(pattern = "HH:mm") LocalTime lunchDeadline,
                             @JsonFormat(pattern = "HH:mm") LocalTime dinnerDeadline,
                             int fatigueWindowDays, int fatigueThreshold,
                             Long myMemberId, Role myRole, int memberCount) {
        public static FamilyView of(Family f, FamilyMember me, int memberCount) {
            // 邀请码只给管理员和掌厨看，避免孩子随手转发
            String code = me.getRole().canCook() ? f.getInviteCode() : null;
            return new FamilyView(f.getId(), f.getName(), code, f.getBreakfastDeadline(), f.getLunchDeadline(),
                    f.getDinnerDeadline(), f.getFatigueWindowDays(), f.getFatigueThreshold(),
                    me.getId(), me.getRole(), memberCount);
        }
    }

    public record MemberView(Long id, Long userId, String displayName, Role role, String dietaryNotes,
                             String allergies, String username, boolean wechatBound, LocalDateTime joinedAt) {
        public static MemberView of(FamilyMember m, AppUser u) {
            return new MemberView(m.getId(), m.getUserId(), m.getDisplayName(), m.getRole(), m.getDietaryNotes(),
                    m.getAllergies(), u == null ? null : u.getUsername(), u != null && u.getWxOpenid() != null,
                    m.getJoinedAt());
        }
    }

    /** 管理员直接创建成员（如给孩子或老人建一个用户名密码账号，在 H5 / 平板上用）。 */
    public record CreateMemberRequest(
            @NotBlank(message = "称呼不能为空") @Size(max = 64) String displayName,
            Role role,
            @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 32) String username,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 64) String password,
            @Size(max = 255) String dietaryNotes,
            @Size(max = 255) String allergies) {}

    /** role 仅管理员可改；其他字段本人或管理员可改。 */
    public record UpdateMemberRequest(
            @Size(max = 64) String displayName,
            Role role,
            @Size(max = 255) String dietaryNotes,
            @Size(max = 255) String allergies) {}
}
