package com.zzwen6.familymeal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void bootstrapAdminCanLoginAndSeeDefaultFamily() throws Exception {
        Res r = post("/api/auth/login", null, Map.of("username", "admin", "password", "admin-pass-123"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("token").asText()).isNotBlank();
        assertThat(r.body().at("/user/systemAdmin").asBoolean()).isTrue();
        assertThat(r.body().at("/families/0/name").asText()).isEqualTo("测试之家");
        assertThat(r.body().at("/families/0/myRole").asText()).isEqualTo("ADMIN");

        Res me = get("/api/auth/me", r.body().get("token").asText());
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().at("/user/username").asText()).isEqualTo("admin");
    }

    @Test
    void wrongPasswordAndMissingTokenUseUnifiedErrorFormat() throws Exception {
        Res bad = post("/api/auth/login", null, Map.of("username", "admin", "password", "nope"));
        assertThat(bad.status()).isEqualTo(401);
        assertThat(bad.body().get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(bad.body().get("message").asText()).isEqualTo("用户名或密码错误");

        Res anon = get("/api/auth/me", null);
        assertThat(anon.status()).isEqualTo(401);
        assertThat(anon.body().get("code").asText()).isEqualTo("UNAUTHORIZED");

        Res garbage = get("/api/auth/me", "not-a-jwt");
        assertThat(garbage.status()).isEqualTo(401);

        Res invalid = post("/api/auth/register", null, Map.of("username", "x", "password", "1", "nickname", ""));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(invalid.body().get("fieldErrors").has("username")).isTrue();
    }

    @Test
    void repeatedFailuresLockTheAccountViaRedis() throws Exception {
        post("/api/auth/register", null, Map.of("username", "locky", "password", "secret-1", "nickname", "小锁"));
        for (int i = 0; i < 5; i++) {
            assertThat(post("/api/auth/login", null, Map.of("username", "locky", "password", "wrong")).status())
                    .isEqualTo(401);
        }
        Res locked = post("/api/auth/login", null, Map.of("username", "locky", "password", "secret-1"));
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.body().get("code").asText()).isEqualTo("LOGIN_LOCKED");
    }

    @Test
    void wechatMockLoginIsStablePerCode() throws Exception {
        Res a = post("/api/auth/wx-login", null, Map.of("code", "dev-code-1", "nickname", "小明"));
        assertThat(a.status()).isEqualTo(200);
        assertThat(a.body().at("/user/wechatBound").asBoolean()).isTrue();
        assertThat(a.body().at("/user/nickname").asText()).isEqualTo("小明");
        Res b = post("/api/auth/wx-login", null, Map.of("code", "dev-code-1"));
        assertThat(b.body().at("/user/id").asLong()).isEqualTo(a.body().at("/user/id").asLong());
    }

    @Test
    void registerWithInviteCodeJoinsFamilyAsMember() throws Exception {
        String admin = adminToken();
        Res fam = get("/api/families", admin);
        String invite = fam.body().at("/0/inviteCode").asText();
        assertThat(invite).hasSize(8);

        Res reg = post("/api/auth/register", null,
                Map.of("username", "dad", "password", "secret-1", "nickname", "爸爸", "inviteCode", invite.toLowerCase()));
        assertThat(reg.status()).isEqualTo(201);
        assertThat(reg.body().at("/families/0/myRole").asText()).isEqualTo("MEMBER");
        // 普通成员看不到邀请码
        assertThat(reg.body().at("/families/0/inviteCode").isMissingNode()).isTrue();

        Res dup = post("/api/auth/register", null, Map.of("username", "dad", "password", "secret-1", "nickname", "爸爸2"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.body().get("code").asText()).isEqualTo("USERNAME_TAKEN");
    }
}
