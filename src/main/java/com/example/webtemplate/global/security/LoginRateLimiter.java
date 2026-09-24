package com.example.webtemplate.global.security;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(1)).build();

    public boolean allow(String remoteAddress) {
        return attempts.get(remoteAddress, key -> new AtomicInteger()).incrementAndGet() <= 20;
    }

    public void clear() { attempts.invalidateAll(); }
}
