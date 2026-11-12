package com.zzwen6.familymeal.notify;

import com.zzwen6.familymeal.common.Enums.WechatStatus;
import com.zzwen6.familymeal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 微信订阅消息推送（开饭提醒等）。
 *
 * TODO(wechat-subscribe): 配置 WECHAT_APPID / WECHAT_SECRET / WECHAT_DINNER_READY_TEMPLATE_ID 后实现：
 *   1. 用 appid+secret 获取并缓存 access_token（建议放 Redis，提前 5 分钟刷新）；
 *   2. 小程序端在用户点「提醒我开饭」时调用 wx.requestSubscribeMessage 取得一次性授权；
 *   3. 调用 POST https://api.weixin.qq.com/cgi-bin/message/subscribe/send 给每个 openid 发送。
 * 未配置时只返回 SKIPPED，站内通知（/notifications 接口）照常写入，前端可轮询展示。
 */
@Component
public class WechatNotifier {
    private static final Logger log = LoggerFactory.getLogger(WechatNotifier.class);
    private final AppProperties.Wechat cfg;

    public WechatNotifier(AppProperties props) {
        this.cfg = props.wechat();
    }

    public WechatStatus send(List<String> openids, String title, String content) {
        if (!cfg.configured() || cfg.dinnerReadyTemplateId() == null || cfg.dinnerReadyTemplateId().isBlank()) {
            log.info("微信订阅消息未配置，跳过推送：{} - {}（{} 个接收人）", title, content, openids.size());
            return WechatStatus.SKIPPED;
        }
        // TODO(wechat-subscribe): 在这里调用订阅消息接口，成功返回 SENT，失败返回 FAILED
        log.info("微信订阅消息推送尚未实现：{} - {}", title, content);
        return WechatStatus.SKIPPED;
    }
}
