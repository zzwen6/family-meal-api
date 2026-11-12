package com.zzwen6.familymeal.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzwen6.familymeal.common.Enums.WechatStatus;
import com.zzwen6.familymeal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信订阅消息推送（开饭提醒，家庭和临时聚餐共用）。
 *
 * <ol>
 *   <li>用 appid + secret 调 {@code /cgi-bin/token} 取 access_token，缓存到 Redis（{@value #TOKEN_KEY}），
 *       比微信给的有效期提前 5 分钟过期，过期后自动重新获取；token 失效（40001/42001）时清缓存重试一次；</li>
 *   <li>给每个有 openid 的接收人 POST {@code /cgi-bin/message/subscribe/send}，模板字段名按
 *       {@link AppProperties.Subscribe} 映射；</li>
 *   <li>未配置 WECHAT_APPID / WECHAT_SECRET / WECHAT_DINNER_READY_TEMPLATE_ID 时直接返回 SKIPPED；</li>
 *   <li>任何失败只记日志并返回 FAILED，不抛异常，不影响开饭流程和站内通知。</li>
 * </ol>
 * 用户需要先在小程序里点「提醒我开饭」（wx.requestSubscribeMessage）授权，一次授权只能收一条；
 * 没授权的用户微信会返回 43101，这里记为该用户发送失败。
 */
@Component
public class WechatNotifier {
    private static final Logger log = LoggerFactory.getLogger(WechatNotifier.class);
    static final String TOKEN_KEY = "fm:wx:access_token";
    static final Duration REFRESH_AHEAD = Duration.ofMinutes(5);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppProperties.Wechat cfg;
    private final RestClient http;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final Clock clock;

    @Autowired
    public WechatNotifier(AppProperties props, RestClient.Builder builder, StringRedisTemplate redis,
                          ObjectMapper om, Clock clock) {
        this(props.wechat(), builder, redis, om, clock);
    }

    WechatNotifier(AppProperties.Wechat cfg, RestClient.Builder builder, StringRedisTemplate redis,
                   ObjectMapper om, Clock clock) {
        this.cfg = cfg;
        String base = cfg.apiBase() == null || cfg.apiBase().isBlank() ? "https://api.weixin.qq.com" : cfg.apiBase();
        this.http = builder.baseUrl(base).build();
        this.redis = redis;
        this.om = om;
        this.clock = clock;
    }

    public boolean enabled() {
        return cfg.subscribeConfigured();
    }

    /** 给小程序端用：要请求订阅授权的模板 ID（未配置时为空）。 */
    public String dinnerReadyTemplateId() {
        return enabled() ? cfg.dinnerReadyTemplateId() : null;
    }

    public WechatStatus send(List<String> openids, String title, String content) {
        return send(openids, title, content, NotificationService.FAMILY_PAGE);
    }

    /**
     * @param page 点消息打开的小程序页面，如 pages/gathering/detail?id=1
     * @return SKIPPED 未配置或没有接收人；SENT 至少一人发送成功；FAILED 全部失败
     */
    public WechatStatus send(List<String> openids, String title, String content, String page) {
        if (!enabled()) {
            log.info("微信订阅消息未配置，跳过推送：{} - {}（{} 个接收人）", title, content, openids.size());
            return WechatStatus.SKIPPED;
        }
        if (openids.isEmpty()) {
            log.info("没有绑定微信的接收人，跳过订阅消息：{}", title);
            return WechatStatus.SKIPPED;
        }
        String token;
        try {
            token = accessToken(false);
        } catch (Exception e) {
            log.warn("获取微信 access_token 失败，订阅消息未发送：{}", e.getMessage());
            return WechatStatus.FAILED;
        }
        Map<String, Object> data = templateData(title, content);
        int sent = 0;
        for (String openid : openids) {
            try {
                int code = sendOne(token, openid, page, data);
                if (code == 40001 || code == 42001) {
                    // token 被提前作废（比如别处重新获取过），清缓存重试一次
                    token = accessToken(true);
                    code = sendOne(token, openid, page, data);
                }
                if (code == 0) sent++;
                else if (code == 43101) log.info("用户 {} 未授权订阅消息（43101），跳过", mask(openid));
                else log.warn("订阅消息发送失败 openid={} errcode={}", mask(openid), code);
            } catch (Exception e) {
                log.warn("订阅消息发送异常 openid={}：{}", mask(openid), e.getMessage());
            }
        }
        log.info("订阅消息「{}」发送 {}/{}", title, sent, openids.size());
        return sent > 0 ? WechatStatus.SENT : WechatStatus.FAILED;
    }

    Map<String, Object> templateData(String title, String content) {
        AppProperties.Subscribe s = cfg.subscribeOrDefault();
        Map<String, Object> data = new LinkedHashMap<>();
        put(data, s.titleField(), title);
        put(data, s.contentField(), content);
        put(data, s.timeField(), LocalDateTime.now(clock).format(TIME_FMT));
        return data;
    }

    private static void put(Map<String, Object> data, String field, String value) {
        if (field == null || field.isBlank() || value == null) return;
        String f = field.trim();
        // thing.DATA 最多 20 个字，phrase 5 个字，超出微信会直接拒收
        String v = f.startsWith("thing") ? cut(value, 20) : f.startsWith("phrase") ? cut(value, 5) : value;
        data.put(f, Map.of("value", v));
    }

    private static String cut(String s, int max) {
        return s.codePointCount(0, s.length()) <= max ? s : s.substring(0, s.offsetByCodePoints(0, max - 1)) + "…";
    }

    private int sendOne(String token, String openid, String page, Map<String, Object> data) throws Exception {
        AppProperties.Subscribe s = cfg.subscribeOrDefault();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", cfg.dinnerReadyTemplateId());
        if (page != null) body.put("page", page);
        if (s.miniprogramState() != null && !s.miniprogramState().isBlank()) body.put("miniprogram_state", s.miniprogramState());
        if (s.lang() != null && !s.lang().isBlank()) body.put("lang", s.lang());
        body.put("data", data);
        String resp = http.post().uri(u -> u.path("/cgi-bin/message/subscribe/send").queryParam("access_token", token).build())
                .contentType(MediaType.APPLICATION_JSON).body(om.writeValueAsString(body))
                .retrieve().body(String.class);
        JsonNode n = om.readTree(resp == null ? "{}" : resp);
        return n.path("errcode").asInt(0);
    }

    /** 读 Redis 缓存；没有或强制刷新时向微信重新获取，并按 expires_in − 5 分钟缓存。 */
    String accessToken(boolean forceRefresh) throws Exception {
        if (!forceRefresh) {
            String cached = safeGet();
            if (cached != null && !cached.isBlank()) return cached;
        } else {
            safeDelete();
        }
        String resp = http.get().uri(u -> u.path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", cfg.appid())
                        .queryParam("secret", cfg.secret()).build())
                .retrieve().body(String.class);
        JsonNode n = om.readTree(resp == null ? "{}" : resp);
        if (n.path("errcode").asInt(0) != 0 || !n.hasNonNull("access_token")) {
            throw new IllegalStateException("errcode=" + n.path("errcode").asInt() + " errmsg=" + n.path("errmsg").asText());
        }
        String token = n.get("access_token").asText();
        long ttl = Math.max(60, n.path("expires_in").asLong(7200) - REFRESH_AHEAD.toSeconds());
        try {
            redis.opsForValue().set(TOKEN_KEY, token, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("access_token 写入 Redis 失败（不影响本次发送）：{}", e.getMessage());
        }
        return token;
    }

    private String safeGet() {
        try {
            return redis.opsForValue().get(TOKEN_KEY);
        } catch (Exception e) {
            log.warn("读取 Redis 中的 access_token 失败，直接向微信获取：{}", e.getMessage());
            return null;
        }
    }

    private void safeDelete() {
        try {
            redis.delete(TOKEN_KEY);
        } catch (Exception e) {
            log.debug("删除 access_token 缓存失败：{}", e.getMessage());
        }
    }

    private static String mask(String openid) {
        return openid.length() <= 8 ? "***" : openid.substring(0, 4) + "***" + openid.substring(openid.length() - 4);
    }
}
