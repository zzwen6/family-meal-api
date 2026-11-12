package com.zzwen6.familymeal.auth;

import com.zzwen6.familymeal.auth.AuthDtos.*;
import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.family.FamilyService;
import com.zzwen6.familymeal.security.CurrentUser;
import com.zzwen6.familymeal.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final WechatClient wechat;
    private final LoginRateLimiter limiter;
    private final FamilyService families;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, WechatClient wechat,
                       LoginRateLimiter limiter, FamilyService families) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.wechat = wechat;
        this.limiter = limiter;
        this.families = families;
    }

    @Transactional
    public LoginResponse wxLogin(WxLoginRequest req) {
        WechatClient.Session s = wechat.code2Session(req.code());
        AppUser u = users.findByWxOpenid(s.openid()).orElseGet(() -> {
            AppUser nu = new AppUser();
            nu.setWxOpenid(s.openid());
            nu.setWxUnionid(s.unionid());
            nu.setNickname(req.nickname() == null || req.nickname().isBlank() ? "微信用户" : req.nickname().trim());
            nu.setAvatarUrl(req.avatarUrl());
            return users.save(nu);
        });
        if (req.nickname() != null && !req.nickname().isBlank() && "微信用户".equals(u.getNickname())) {
            u.setNickname(req.nickname().trim());
        }
        return respond(u);
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        String username = req.username().trim();
        limiter.check(username);
        AppUser u = users.findByUsername(username).orElse(null);
        if (u == null || u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            limiter.onFailure(username);
            throw BizException.unauthorized("用户名或密码错误");
        }
        limiter.onSuccess(username);
        return respond(u);
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        String username = req.username().trim();
        if (users.existsByUsername(username)) throw BizException.conflict("USERNAME_TAKEN", "用户名已被使用");
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setNickname(req.nickname().trim());
        users.save(u);
        if (req.inviteCode() != null && !req.inviteCode().isBlank()) {
            families.joinFor(u, req.inviteCode(), req.nickname());
        }
        return respond(u);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AppUser u = users.findById(CurrentUser.id()).orElseThrow(() -> BizException.unauthorized("用户不存在"));
        return new MeResponse(UserView.of(u), families.mine());
    }

    @Transactional
    public UserView updateProfile(UpdateProfileRequest req) {
        AppUser u = users.findById(CurrentUser.id()).orElseThrow(() -> BizException.unauthorized("用户不存在"));
        if (req.nickname() != null && !req.nickname().isBlank()) u.setNickname(req.nickname().trim());
        if (req.avatarUrl() != null) u.setAvatarUrl(req.avatarUrl());
        return UserView.of(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        AppUser u = users.findById(CurrentUser.id()).orElseThrow(() -> BizException.unauthorized("用户不存在"));
        if (u.getPasswordHash() != null
                && (req.oldPassword() == null || !encoder.matches(req.oldPassword(), u.getPasswordHash()))) {
            throw BizException.badRequest("原密码不正确");
        }
        if (u.getUsername() == null) throw BizException.badRequest("请先在管理后台为该账号设置用户名");
        u.setPasswordHash(encoder.encode(req.newPassword()));
    }

    private LoginResponse respond(AppUser u) {
        String token = jwt.issue(u.getId(), u.isSystemAdmin());
        return new LoginResponse(token, jwt.ttlSeconds(), UserView.of(u), families.mineFor(u.getId()));
    }
}
