package net.onelitefeather.otis.api;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that only moves when a test says so. Active only for tests that set {@code otis.test.clock=true},
 * so other tests keep the system clock.
 */
@Singleton
@Requires(property = "otis.test.clock", value = "true")
public final class TestClock extends Clock {

    private volatile Instant now = Instant.parse("2026-01-01T12:00:00Z");

    public void set(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
