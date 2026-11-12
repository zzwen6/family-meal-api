package com.zzwen6.familymeal.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzwen6.familymeal.common.Enums.WechatStatus;
import com.zzwen6.familymeal.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 订阅消息：未配置跳过；配置后验证 access_token 缓存与请求体（mock HTTP，不会真的请求微信）。 */
class WechatNotifierTest {
    private static final String BASE = "https://wx.test";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ObjectMapper om = new ObjectMapper();
    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 12, 18, 30).atZone(ZONE).toInstant(), ZONE);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private WechatNotifier notifier(String appid, String secret, String templateId, AppProperties.Subscribe sub) {
        var cfg = new AppProperties.Wechat(appid, secret, false, templateId, BASE, sub);
        return new WechatNotifier(cfg, builder, redis, om, clock);
    }

    @Test
    void skipsWhenNotConfigured() {
        assertThat(notifier("", "", "", null).send(List.of("o-1"), "开饭啦", "晚餐好了", "pages/index/index"))
                .isEqualTo(WechatStatus.SKIPPED);
        // 有 appid 没模板 ID 也跳过
        WechatNotifier noTpl = notifier("wx-app", "wx-secret", " ", null);
        assertThat(noTpl.enabled()).isFalse();
        assertThat(noTpl.dinnerReadyTemplateId()).isNull();
        assertThat(noTpl.send(List.of("o-1"), "开饭啦", "晚餐好了", "pages/index/index")).isEqualTo(WechatStatus.SKIPPED);
        verifyNoInteractions(redis);
        server.verify();
    }

    @Test
    void fetchesTokenCachesItAndSendsTemplate() {
        when(ops.get(WechatNotifier.TOKEN_KEY)).thenReturn(null);
        server.expect(once(), requestTo(BASE + "/cgi-bin/token?grant_type=client_credential&appid=wx-app&secret=wx-secret"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/cgi-bin/message/subscribe/send?access_token=TOKEN-1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.touser").value("openid-mom"))
                .andExpect(jsonPath("$.template_id").value("TPL-READY"))
                .andExpect(jsonPath("$.page").value("pages/gathering/detail?id=7"))
                .andExpect(jsonPath("$.miniprogram_state").value("formal"))
                .andExpect(jsonPath("$.data.thing1.value").value("开饭啦"))
                // thing 字段最多 20 个字，超出截断
                .andExpect(jsonPath("$.data.thing2.value").value("一二三四五六七八九十一二三四五六七八九…"))
                .andExpect(jsonPath("$.data.time3.value").value("2026-11-12 18:30"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/cgi-bin/message/subscribe/send?access_token=TOKEN-1"))
                .andExpect(jsonPath("$.touser").value("openid-dad"))
                // 没授权订阅
                .andRespond(withSuccess("{\"errcode\":43101,\"errmsg\":\"user refuse to accept the msg\"}", MediaType.APPLICATION_JSON));

        WechatStatus s = notifier("wx-app", "wx-secret", "TPL-READY", null).send(List.of("openid-mom", "openid-dad"),
                "开饭啦", "一二三四五六七八九十一二三四五六七八九十一二", "pages/gathering/detail?id=7");

        assertThat(s).isEqualTo(WechatStatus.SENT);
        server.verify();
        // 提前 5 分钟过期：7200 - 300
        verify(ops).set(eq(WechatNotifier.TOKEN_KEY), eq("TOKEN-1"), eq(Duration.ofSeconds(6900)));
    }

    @Test
    void usesCachedTokenAndCustomFieldMapping() {
        when(ops.get(WechatNotifier.TOKEN_KEY)).thenReturn("CACHED");
        var sub = new AppProperties.Subscribe("thing4", "", "date2", "trial", "zh_CN");
        server.expect(once(), requestTo(BASE + "/cgi-bin/message/subscribe/send?access_token=CACHED"))
                .andExpect(jsonPath("$.data.thing4.value").value("开饭啦"))
                .andExpect(jsonPath("$.data.date2.value").value("2026-11-12 18:30"))
                .andExpect(jsonPath("$.data.thing2").doesNotExist())
                .andExpect(jsonPath("$.miniprogram_state").value("trial"))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        assertThat(notifier("wx-app", "wx-secret", "TPL", sub).send(List.of("o-1"), "开饭啦", "好了", "pages/index/index"))
                .isEqualTo(WechatStatus.SENT);
        server.verify();
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void refreshesExpiredTokenOnce() {
        when(ops.get(WechatNotifier.TOKEN_KEY)).thenReturn("STALE");
        server.expect(once(), requestTo(BASE + "/cgi-bin/message/subscribe/send?access_token=STALE"))
                .andRespond(withSuccess("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/cgi-bin/token?grant_type=client_credential&appid=wx-app&secret=wx-secret"))
                .andRespond(withSuccess("{\"access_token\":\"FRESH\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/cgi-bin/message/subscribe/send?access_token=FRESH"))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        assertThat(notifier("wx-app", "wx-secret", "TPL", null).send(List.of("o-1"), "开饭啦", "好了", "p"))
                .isEqualTo(WechatStatus.SENT);
        server.verify();
        verify(redis).delete(WechatNotifier.TOKEN_KEY);
        verify(ops).set(eq(WechatNotifier.TOKEN_KEY), eq("FRESH"), any(Duration.class));
    }

    @Test
    void failuresReturnFailedWithoutThrowing() {
        when(ops.get(WechatNotifier.TOKEN_KEY)).thenReturn(null);
        server.expect(once(), requestTo(BASE + "/cgi-bin/token?grant_type=client_credential&appid=wx-app&secret=bad"))
                .andRespond(withSuccess("{\"errcode\":40125,\"errmsg\":\"invalid appsecret\"}", MediaType.APPLICATION_JSON));
        assertThat(notifier("wx-app", "bad", "TPL", null).send(List.of("o-1"), "开饭啦", "好了", "p"))
                .isEqualTo(WechatStatus.FAILED);
        server.verify();

        // 发送接口 500 也只返回 FAILED
        setUp();
        when(ops.get(WechatNotifier.TOKEN_KEY)).thenReturn("T");
        server.expect(once(), requestTo(BASE + "/cgi-bin/message/subscribe/send?access_token=T")).andRespond(withServerError());
        assertThat(notifier("wx-app", "wx-secret", "TPL", null).send(List.of("o-1"), "开饭啦", "好了", "p"))
                .isEqualTo(WechatStatus.FAILED);
        server.verify();
    }

    @Test
    void noRecipientsIsSkipped() {
        assertThat(notifier("wx-app", "wx-secret", "TPL", null).send(List.of(), "开饭啦", "好了", "p"))
                .isEqualTo(WechatStatus.SKIPPED);
        verifyNoInteractions(redis);
    }
}
