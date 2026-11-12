package com.zzwen6.familymeal.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 小程序登录：wx.login 拿到的 code → 服务端 jscode2session 换 openid。
 * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
 */
@Component
public class WechatClient {
    private static final Logger log = LoggerFactory.getLogger(WechatClient.class);

    public record Session(String openid, String unionid) {}

    private final AppProperties.Wechat cfg;
    private final RestClient http;
    private final ObjectMapper om;

    public WechatClient(AppProperties props, RestClient.Builder builder, ObjectMapper om) {
        this.cfg = props.wechat();
        this.http = builder.baseUrl(cfg.apiBase()).build();
        this.om = om;
    }

    public Session code2Session(String code) {
        if (!cfg.configured()) {
            if (cfg.mockEnabled()) {
                // 开发模式：未配置 appid 时，用 code 生成一个稳定的假 openid，方便 H5/开发者工具联调
                log.warn("WECHAT_APPID 未配置，wx-login 使用 mock openid（仅限开发环境）");
                return new Session("mock-" + code, null);
            }
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_NOT_CONFIGURED",
                    "服务端未配置小程序 AppID / AppSecret");
        }
        String body;
        try {
            body = http.get().uri(u -> u.path("/sns/jscode2session")
                            .queryParam("appid", cfg.appid())
                            .queryParam("secret", cfg.secret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code").build())
                    .retrieve().body(String.class);
        } catch (RestClientException e) {
            log.error("调用 jscode2session 失败", e);
            throw new BizException(HttpStatus.BAD_GATEWAY, "WECHAT_UNAVAILABLE", "微信登录服务暂不可用");
        }
        try {
            JsonNode n = om.readTree(body);
            if (n.hasNonNull("errcode") && n.get("errcode").asInt() != 0) {
                log.warn("jscode2session errcode={} errmsg={}", n.get("errcode").asInt(), n.path("errmsg").asText());
                throw BizException.unauthorized("微信登录失败，请重试");
            }
            String openid = n.path("openid").asText(null);
            if (openid == null) throw BizException.unauthorized("微信登录失败，请重试");
            return new Session(openid, n.path("unionid").asText(null));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_GATEWAY, "WECHAT_UNAVAILABLE", "微信登录返回无法解析");
        }
    }
}
