package com.zzwen6.familymeal.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** 让 JPA 回调等非 Bean 代码也使用统一时区的 {@link Clock}。 */
@Component
public class TimeHolder {
    private static volatile Clock clock = Clock.systemDefaultZone();

    public TimeHolder(Clock clock) {
        TimeHolder.clock = clock;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
