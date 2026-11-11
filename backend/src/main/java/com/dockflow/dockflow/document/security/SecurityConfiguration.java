package com.dockflow.dockflow.document.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain docflowSecurity(
        HttpSecurity http,
        @Value("${docflow.security.enabled:false}") boolean securityEnabled,
        BearerTokenResolver bearerTokenResolver
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> { })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!securityEnabled) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt ->
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.GET, "/documents", "/documents/**").permitAll()
                .requestMatchers("/documents/**", "/logs/**", "/ws/**").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(bearerTokenResolver)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String token = delegate.resolve(request);
            if (token != null) {
                return token;
            }
            if (request.getRequestURI().startsWith("/ws/")) {
                return request.getParameter("access_token");
            }
            return null;
        };
    }

    @Bean
    @ConditionalOnProperty(name = "docflow.security.enabled", havingValue = "true")
    JwtDecoder jwtDecoder(@Value("${docflow.neon.auth.jwks-url:}") String jwksUrl) {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            throw new IllegalStateException("NEON_AUTH_JWKS_URL is required when DOCFLOW_SECURITY_ENABLED=true");
        }
        return NimbusJwtDecoder.withJwkSetUri(jwksUrl)
            .discoverJwsAlgorithms()
            .build();
    }
}
