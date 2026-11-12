package com.zzwen6.familymeal.auth;

import com.zzwen6.familymeal.family.FamilyDtos.FamilyView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record WxLoginRequest(@NotBlank(message = "code 不能为空") String code,
                                 @Size(max = 64) String nickname,
                                 @Size(max = 512) String avatarUrl) {}

    public record LoginRequest(@NotBlank(message = "请输入用户名") String username,
                               @NotBlank(message = "请输入密码") String password) {}

    /** H5 注册：用户名密码 + 可选邀请码（填了就直接加入家庭）。 */
    public record RegisterRequest(
            @NotBlank(message = "请输入用户名") @Size(min = 3, max = 32, message = "用户名 3-32 位")
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点和横线") String username,
            @NotBlank(message = "请输入密码") @Size(min = 6, max = 64, message = "密码至少 6 位") String password,
            @NotBlank(message = "请输入昵称") @Size(max = 64) String nickname,
            @Size(max = 16) String inviteCode) {}

    public record UpdateProfileRequest(@Size(max = 64) String nickname, @Size(max = 512) String avatarUrl) {}

    public record ChangePasswordRequest(String oldPassword,
                                        @NotBlank @Size(min = 6, max = 64, message = "密码至少 6 位") String newPassword) {}

    public record UserView(Long id, String nickname, String avatarUrl, String username, boolean wechatBound,
                           boolean systemAdmin) {
        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getNickname(), u.getAvatarUrl(), u.getUsername(),
                    u.getWxOpenid() != null, u.isSystemAdmin());
        }
    }

    public record LoginResponse(String token, long expiresIn, UserView user, List<FamilyView> families) {}

    public record MeResponse(UserView user, List<FamilyView> families) {}
}
