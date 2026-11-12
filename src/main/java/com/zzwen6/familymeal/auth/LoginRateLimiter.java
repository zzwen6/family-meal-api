package com.zzwen6.familymeal.auth;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 用 Redis 记录密码登录失败次数，超过阈值锁定一段时间。Redis 不可用时放行（只记日志）。 */
@Component
public class LoginRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private final StringRedisTemplate redis;
    private final AppProperties.Login cfg;

    public LoginRateLimiter(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.cfg = props.login();
    }

    private String key(String username) {
        return "fm:login-fail:" + username.toLowerCase();
    }

    public void check(String username) {
        try {
            String v = redis.opsForValue().get(key(username));
            if (v != null && Integer.parseInt(v) >= cfg.maxFailures()) {
                throw new BizException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                        "密码错误次数过多，请 " + cfg.lockMinutes() + " 分钟后再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过登录限流：{}", e.getMessage());
        }
    }

    public void onFailure(String username) {
        try {
            Long n = redis.opsForValue().increment(key(username));
            if (n != null && n == 1L) redis.expire(key(username), Duration.ofMinutes(cfg.lockMinutes()));
        } catch (Exception e) {
            log.warn("Redis 不可用，未记录登录失败：{}", e.getMessage());
        }
    }

    public void onSuccess(String username) {
        try {
            redis.delete(key(username));
        } catch (Exception e) {
            log.warn("Redis 不可用：{}", e.getMessage());
        }
    }
}
