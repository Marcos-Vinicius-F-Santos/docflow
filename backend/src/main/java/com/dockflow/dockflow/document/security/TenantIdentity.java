package com.dockflow.dockflow.document.security;

import java.util.UUID;

public record TenantIdentity(UUID tenantId, String subject, Role role) {

    public enum Role {
        USER,
        OPERATOR,
        ADMIN;

        public boolean includes(Role required) {
            return ordinal() >= required.ordinal();
        }
    }
}
