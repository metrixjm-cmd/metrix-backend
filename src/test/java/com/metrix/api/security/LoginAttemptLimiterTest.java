package com.metrix.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {

    @Test
    void failuresDoNotCrossTenants() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("TACOS-A3F2", "ADMIN001");
        }

        assertTrue(limiter.isBlocked("TACOS-A3F2", "ADMIN001"));
        assertFalse(limiter.isBlocked("PIZZA-91BE", "ADMIN001"));
        assertFalse(limiter.isBlocked("METRIX", "ADMIN001"));
    }

    @Test
    void successClearsOnlyThatPair() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        limiter.recordFailure("TACOS-A3F2", "ADMIN001");
        limiter.recordSuccess("TACOS-A3F2", "ADMIN001");
        assertFalse(limiter.isBlocked("TACOS-A3F2", "ADMIN001"));
    }
}
