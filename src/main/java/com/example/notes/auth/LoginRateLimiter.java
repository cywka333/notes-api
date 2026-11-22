package com.example.notes.auth;


import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

final class LoginRateLimiter {

    private final SequencedMap<String, Client> clients = new LinkedHashMap<>();
    private final Bandwidth limit;
    private final long windowNanos;
    private final TimeMeter clock;

    LoginRateLimiter(LoginRateLimitProperties properties, TimeMeter clock) {
        Duration period = Duration.ofSeconds(properties.windowSeconds());
        int attempts = properties.attempts();
        this.limit = Bandwidth.builder().capacity(attempts).refillIntervally(attempts, period).build();
        this.windowNanos = period.toNanos();
        this.clock = clock;
    }

    synchronized long retryAfterSeconds(String clientIp) {
        long now = clock.currentTimeNanos();
        while (!clients.isEmpty() && now - clients.firstEntry().getValue().lastAttemptNanos() >= windowNanos) {
            clients.pollFirstEntry();
        }
        Client client = clients.get(clientIp);
        Bucket bucket = client == null ? newBucket() : client.bucket();
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        clients.putLast(clientIp, new Client(bucket, clock.currentTimeNanos()));
        return probe.isConsumed() ? 0 : Math.ceilDiv(probe.getNanosToWaitForRefill(), 1_000_000_000L);
    }

    private Bucket newBucket() {
        return Bucket.builder().withCustomTimePrecision(clock).addLimit(limit).build();
    }

    private record Client(Bucket bucket, long lastAttemptNanos) {
    }
}
