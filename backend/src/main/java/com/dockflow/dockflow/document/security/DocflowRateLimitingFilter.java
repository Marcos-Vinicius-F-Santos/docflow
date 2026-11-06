package com.dockflow.dockflow.document.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Small instance-local guard for the free staging deployment. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "docflow.security.enabled", havingValue = "true")
public class DocflowRateLimitingFilter extends OncePerRequestFilter {

    private final int uploadsPerMinute;
    private final int retriesPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public DocflowRateLimitingFilter(
        @Value("${docflow.security.rate-limit.uploads-per-minute:10}") int uploadsPerMinute,
        @Value("${docflow.security.rate-limit.retries-per-minute:30}") int retriesPerMinute
    ) {
        this(uploadsPerMinute, retriesPerMinute, Clock.systemUTC());
    }

    DocflowRateLimitingFilter(int uploadsPerMinute, int retriesPerMinute, Clock clock) {
        if (uploadsPerMinute < 1 || retriesPerMinute < 1 || clock == null) {
            throw new IllegalArgumentException("positive rate limits and a clock are required");
        }
        this.uploadsPerMinute = uploadsPerMinute;
        this.retriesPerMinute = retriesPerMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !("/documents".equals(path) || path.matches("/documents/[0-9a-fA-F-]+/retry"));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String operation = "/documents".equals(request.getRequestURI()) ? "upload" : "retry";
        int limit = "upload".equals(operation) ? uploadsPerMinute : retriesPerMinute;
        String key = operation + ":" + clientAddress(request);
        Window window = windows.compute(key, (ignored, current) -> {
            Instant now = clock.instant();
            if (current == null || current.expiresAt().isBefore(now)) {
                return new Window(now.plus(Duration.ofMinutes(1)), new AtomicInteger(1));
            }
            current.count().incrementAndGet();
            return current;
        });

        if (window.count().get() > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientAddress(HttpServletRequest request) {
        // Do not trust forwarded headers until the service is behind an
        // explicitly configured trusted proxy.
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private record Window(Instant expiresAt, AtomicInteger count) {
    }
}
