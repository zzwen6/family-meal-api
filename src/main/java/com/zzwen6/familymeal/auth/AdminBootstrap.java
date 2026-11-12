package com.zzwen6.familymeal.auth;

import com.zzwen6.familymeal.config.AppProperties;
import com.zzwen6.familymeal.family.FamilyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次启动：按 ADMIN_USERNAME / ADMIN_PASSWORD 创建管理员和默认家庭。已存在则不动。 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final AppProperties props;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final FamilyService families;

    public AdminBootstrap(AppProperties props, AppUserRepository users, PasswordEncoder encoder, FamilyService families) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.families = families;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin a = props.admin();
        if (a == null || a.username() == null || a.username().isBlank()) return;
        if (users.existsByUsername(a.username())) return;
        if (a.password() == null || a.password().isBlank()) {
            log.warn("ADMIN_PASSWORD 未设置，跳过创建管理员 {}", a.username());
            return;
        }
        AppUser u = new AppUser();
        u.setUsername(a.username());
        u.setPasswordHash(encoder.encode(a.password()));
        u.setNickname("管理员");
        u.setSystemAdmin(true);
        users.save(u);
        families.createFor(u, a.familyName() == null || a.familyName().isBlank() ? "我的家" : a.familyName(), "管理员");
        log.info("已创建管理员 {} 和默认家庭", a.username());
    }
}
