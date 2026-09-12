package com.metrix.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetRequestLimiterTest {

    @Test
    void blocksAfterMaxAttempts() {
        PasswordResetRequestLimiter limiter = new PasswordResetRequestLimiter();
        for (int i = 0; i < PasswordResetRequestLimiter.MAX_ATTEMPTS; i++) {
            limiter.recordAttempt("TACOS-A3F2", "ADMIN001");
        }
        assertTrue(limiter.isBlocked("TACOS-A3F2", "ADMIN001"));
        assertFalse(limiter.isBlocked("TACOS-A3F2", "OTRO"));
        assertFalse(limiter.isBlocked("METRIX", "ADMIN001"));
    }
}
