package com.zzwen6.familymeal.security;

import com.zzwen6.familymeal.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static AuthUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthUser u) return u;
        throw BizException.unauthorized("请先登录");
    }

    public static Long id() { return get().userId(); }
}
