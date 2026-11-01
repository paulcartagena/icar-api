package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.exception.ApiException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Limits {@code POST /auth/login} attempts per client IP against brute force and credential stuffing.
 * <p>
 * The buckets live in memory, so the limit is per instance: with more than one instance it would need a shared
 * store (e.g. Bucket4j + Redis).
 */
class LoginRateLimitFilter extends OncePerRequestFilter {

    // Past this many tracked IPs, buckets that are full again (no recent attempts) are dropped,
    // so requests from many different IPs can't grow the map without limit
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AuthProperties.LoginRateLimit limit;
    private final HandlerExceptionResolver exceptionResolver;

    LoginRateLimitFilter(AuthProperties.LoginRateLimit limit, HandlerExceptionResolver exceptionResolver) {
        this.limit = limit;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.values().removeIf(bucket -> bucket.getAvailableTokens() == limit.capacity());
        }

        // Behind a proxy, Tomcat already resolves the real client IP from X-Forwarded-For
        // (server.forward-headers-strategy: native, which only trusts private-network proxies).
        // Reading the header here would let a client skip the limit by sending a different value each time.
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        exceptionResolver.resolveException(request, response, null,
                ApiException.tooManyRequests("Too many login attempts. Try again later."));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.refillPeriod()))
                .build();
    }
}
