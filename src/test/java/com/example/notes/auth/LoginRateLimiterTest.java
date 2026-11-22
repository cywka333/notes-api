package com.example.notes.auth;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private final TestClock clock = new TestClock();
    private final String ip = "192.0.2.1";

    @Test
    void roundsRemainingTimeUpAndReopensAtTheConfiguredBoundary() {
        LoginRateLimiter limiter = new LoginRateLimiter(new LoginRateLimitProperties(2, 3), clock);
        assertThat(limiter.retryAfterSeconds(ip)).isZero();
        assertThat(limiter.retryAfterSeconds(ip)).isZero();
        assertThat(limiter.retryAfterSeconds(ip)).isEqualTo(3);

        clock.elapsed = Duration.ofMillis(2400);
        assertThat(limiter.retryAfterSeconds(ip)).isEqualTo(1);

        clock.elapsed = Duration.ofSeconds(3);
        assertThat(limiter.retryAfterSeconds(ip)).isZero();
        assertThat(limiter.retryAfterSeconds(ip)).isZero();
        assertThat(limiter.retryAfterSeconds(ip)).isEqualTo(3);
    }

    @Test
    void concurrentAttemptsShareOneAllowance() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter(new LoginRateLimitProperties(5, 60), clock);
        var start = new CountDownLatch(1);
        var attempts = new ArrayList<Future<Long>>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 12; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return limiter.retryAfterSeconds(ip);
                }));
            }
            start.countDown();
            var delays = new ArrayList<Long>();
            for (var attempt : attempts) {
                delays.add(attempt.get(5, TimeUnit.SECONDS));
            }
            assertThat(delays).filteredOn(delay -> delay == 0).hasSize(5);
            assertThat(delays).filteredOn(delay -> delay == 60).hasSize(7);
        }
    }

    private static final class TestClock implements TimeMeter {
        private Duration elapsed = Duration.ZERO;

        @Override
        public long currentTimeNanos() {
            return elapsed.toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
