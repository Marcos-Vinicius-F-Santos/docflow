package com.dockflow.dockflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.dockflow.dockflow.test.PostgresOnlyIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresOnlyIntegrationTest
class DockflowApplicationTests extends IntegrationTestBase {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertTrue(applicationContext.containsBean("documentReconciliationScheduler"));
	}

}
