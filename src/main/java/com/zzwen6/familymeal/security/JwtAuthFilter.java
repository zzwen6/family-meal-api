package com.zzwen6.familymeal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = bearer(req.getHeader("Authorization"));
        // 小程序 <image>/<video> 和 H5 的 src 带不上 Authorization，仅允许媒体下载用 query
        if (token == null && "GET".equalsIgnoreCase(req.getMethod()) && isMediaPath(req.getRequestURI())) {
            String q = req.getParameter("access_token");
            if (q != null && !q.isBlank()) token = q.trim();
        }
        if (token != null) {
            jwt.parse(token).ifPresent(u -> {
                var authorities = u.systemAdmin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(u, null, authorities));
            });
        }
        chain.doFilter(req, res);
    }

    private static String bearer(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            String t = header.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private static boolean isMediaPath(String uri) {
        return uri != null && uri.contains("/media/");
    }
}
