package com.dockflow.dockflow.document.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Resolves the authenticated Neon subject and its application tenant role. */
@Service
public class TenantIdentityService {

    public static final UUID BOOTSTRAP_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BOOTSTRAP_SUBJECT = "bootstrap";

    private final JdbcTemplate jdbcTemplate;
    private final boolean securityEnabled;
    private final String bootstrapAuthSubject;

    public TenantIdentityService(
        JdbcTemplate jdbcTemplate,
        @Value("${docflow.security.enabled:false}") boolean securityEnabled,
        @Value("${docflow.security.bootstrap-auth-subject:bootstrap}") String bootstrapAuthSubject
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityEnabled = securityEnabled;
        this.bootstrapAuthSubject = bootstrapAuthSubject == null || bootstrapAuthSubject.isBlank()
            ? BOOTSTRAP_SUBJECT
            : bootstrapAuthSubject.trim();
    }

    public boolean isEnforced() {
        return securityEnabled;
    }

    @Transactional
    public TenantIdentity current() {
        if (!securityEnabled) {
            return new TenantIdentity(BOOTSTRAP_TENANT_ID, BOOTSTRAP_SUBJECT, TenantIdentity.Role.ADMIN);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("An authenticated identity is required");
        }

        String subject = authentication.getName();
        String email = null;
        String name = null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            email = jwt.getClaimAsString("email");
            name = jwt.getClaimAsString("name");
        }

        java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT tenant_id, role FROM app_users WHERE auth_subject = ?",
            subject
        );
        if (!rows.isEmpty()) {
            java.util.Map<String, Object> row = rows.get(0);
            return new TenantIdentity(
                (UUID) row.get("tenant_id"),
                subject,
                TenantIdentity.Role.valueOf(String.valueOf(row.get("role")))
            );
        }
        return provision(subject, email, name);
    }

    public UUID currentTenantId() {
        return current().tenantId();
    }

    public void requireOperator() {
        if (!current().role().includes(TenantIdentity.Role.OPERATOR)) {
            throw new AccessDeniedException("Operator role is required");
        }
    }

    private TenantIdentity provision(String subject, String email, String name) {
        if (subject.equals(bootstrapAuthSubject)) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO app_users (id, tenant_id, auth_subject, email, role) VALUES (?, ?, ?, ?, 'ADMIN') ON CONFLICT (auth_subject) DO NOTHING",
                    UUID.randomUUID(), BOOTSTRAP_TENANT_ID, subject, email
                );
            } catch (DataIntegrityViolationException ignored) {
                // A concurrent first request can provision the same subject.
            }
            return new TenantIdentity(BOOTSTRAP_TENANT_ID, subject, TenantIdentity.Role.ADMIN);
        }

        UUID tenantId = UUID.randomUUID();
        String slug = "tenant-" + tenantId.toString().substring(0, 8);
        jdbcTemplate.update(
            "INSERT INTO tenants (id, slug, name) VALUES (?, ?, ?)",
            tenantId, slug, name == null || name.isBlank() ? slug : name
        );
        try {
            jdbcTemplate.update(
                "INSERT INTO app_users (id, tenant_id, auth_subject, email, role) VALUES (?, ?, ?, ?, 'USER')",
                UUID.randomUUID(), tenantId, subject, email
            );
        } catch (DataIntegrityViolationException exception) {
            return current();
        }
        return new TenantIdentity(tenantId, subject, TenantIdentity.Role.USER);
    }
}
