package com.zzwen6.familymeal.notify;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序端点「提醒我开饭」前先问一下：服务端配没配订阅消息、要请求哪个模板。 */
@RestController
@RequestMapping("/api/wechat")
public class WechatSubscribeController {
    public record SubscribeConfig(boolean enabled, String dinnerReadyTemplateId) {}

    private final WechatNotifier notifier;

    public WechatSubscribeController(WechatNotifier notifier) {
        this.notifier = notifier;
    }

    @GetMapping("/subscribe-config")
    public SubscribeConfig config() {
        return new SubscribeConfig(notifier.enabled(), notifier.dinnerReadyTemplateId());
    }
}
