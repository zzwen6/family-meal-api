package com.zzwen6.familymeal.auth;

import com.zzwen6.familymeal.auth.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /** 小程序静默登录：前端 wx.login 拿 code 传上来。 */
    @PostMapping("/wx-login")
    public LoginResponse wxLogin(@Valid @RequestBody WxLoginRequest req) { return service.wxLogin(req); }

    /** 用户名密码登录（管理后台、H5）。 */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) { return service.login(req); }

    /** H5 注册（可带邀请码直接入家）。 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest req) { return service.register(req); }

    @GetMapping("/me")
    public MeResponse me() { return service.me(); }

    @PutMapping("/me")
    public UserView updateProfile(@Valid @RequestBody UpdateProfileRequest req) { return service.updateProfile(req); }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) { service.changePassword(req); }
}
