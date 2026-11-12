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
                         String dinnerReadyTemplateId, String apiBase, Subscribe subscribe) {
        public boolean configured() {
            return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
        }

        /** appid、secret、开饭提醒模板 ID 都配了，才真正发订阅消息。 */
        public boolean subscribeConfigured() {
            return configured() && dinnerReadyTemplateId != null && !dinnerReadyTemplateId.isBlank();
        }

        public Subscribe subscribeOrDefault() {
            return subscribe == null ? Subscribe.DEFAULT : subscribe;
        }
    }

    /**
     * 订阅消息模板的字段映射。模板在微信后台选，字段名（thing1 / time3 …）因模板而异，按自己选的模板填。
     * 某项留空表示该模板没有这个字段，不发送。
     *
     * @param titleField       放标题（如「开饭啦」）的字段，默认 thing1
     * @param contentField     放内容（如「11月12日晚餐做好了」）的字段，默认 thing2
     * @param timeField        放时间的字段，默认 time3（格式 yyyy-MM-dd HH:mm）
     * @param miniprogramState 跳转的小程序版本：formal 正式版 / trial 体验版 / developer 开发版
     * @param lang             zh_CN
     */
    public record Subscribe(String titleField, String contentField, String timeField,
                            String miniprogramState, String lang) {
        public static final Subscribe DEFAULT = new Subscribe("thing1", "thing2", "time3", "formal", "zh_CN");
    }

    /** 首次启动时自动创建的管理员；password 为空则不创建。 */
    public record Admin(String username, String password, String familyName) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Login(int maxFailures, int lockMinutes) {}
}
