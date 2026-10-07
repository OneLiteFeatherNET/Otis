package net.onelitefeather.otis.service;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;

/** Provides the clock services read the time from, so tests can inject a fixed one. */
@Factory
class ClockFactory {

    @Singleton
    @Requires(missingBeans = Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }
}
