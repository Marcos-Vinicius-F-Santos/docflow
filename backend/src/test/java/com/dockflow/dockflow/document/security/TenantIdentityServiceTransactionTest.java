package com.dockflow.dockflow.document.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantIdentityServiceTransactionTest {

    @Test
    void authenticatedResolutionCanProvisionOutsideAnEnclosingReadOnlyTransaction() throws Exception {
        Method method = TenantIdentityService.class.getMethod("currentIfAuthenticated");

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertFalse(transactional.readOnly());
    }
}
