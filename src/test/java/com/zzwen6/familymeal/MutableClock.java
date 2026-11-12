package com.zzwen6.familymeal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 测试用可调时钟。 */
public class MutableClock extends Clock {
    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(LocalDateTime start, ZoneId zone) {
        this.zone = zone;
        set(start);
    }

    public void set(LocalDateTime time) {
        this.instant = time.atZone(zone).toInstant();
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
}
