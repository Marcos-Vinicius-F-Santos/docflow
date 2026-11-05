package com.dockflow.dockflow.document.observability;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** Protects the Prometheus scrape endpoint without exposing application security to other routes. */
@Component
public class PrometheusEndpointSecurityFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final String username;
    private final String password;

    public PrometheusEndpointSecurityFilter(
        @Value("${docflow.metrics.prometheus.enabled:false}") boolean enabled,
        @Value("${docflow.metrics.prometheus.username:}") String username,
        @Value("${docflow.metrics.prometheus.password:}") String password
    ) {
        this.enabled = enabled;
        this.username = username;
        this.password = password;
    }

    @PostConstruct
    void validateConfiguration() {
        if (enabled && (username == null || username.isBlank() || password == null || password.isBlank())) {
            throw new IllegalStateException(
                "DOCFLOW_PROMETHEUS_USERNAME and DOCFLOW_PROMETHEUS_PASSWORD are required when metrics are enabled"
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().equals(request.getContextPath() + "/actuator/prometheus");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAuthorized(request.getHeader("Authorization"))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"docflow-metrics\"");
    }

    private boolean isAuthorized(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        try {
            String decoded = new String(
                Base64.getDecoder().decode(authorization.substring("Basic ".length())),
                StandardCharsets.UTF_8
            );
            byte[] expected = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(decoded.getBytes(StandardCharsets.UTF_8), expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
