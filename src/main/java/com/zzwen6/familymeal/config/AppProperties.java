package com.zzwen6.familymeal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 应用配置，全部可用环境变量覆盖，见 application.yml 与 .env.example。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        Jwt jwt,
        Wechat wechat,
        Admin admin,
        Cors cors,
        Login login) {

    public record Jwt(String secret, long ttlHours, String issuer) {}

    /**
     * 小程序 appid/secret 为空时，wx-login 只在 mockEnabled=true 时可用（开发调试）。
     */
    public record Wechat(String appid, String secret, boolean mockEnabled,
                         String dinnerReadyTemplateId, String apiBase) {
        public boolean configured() {
            return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
        }
    }

    /** 首次启动时自动创建的管理员；password 为空则不创建。 */
    public record Admin(String username, String password, String familyName) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Login(int maxFailures, int lockMinutes) {}
}
