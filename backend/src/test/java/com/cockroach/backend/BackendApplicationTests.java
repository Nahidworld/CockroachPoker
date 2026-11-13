package com.cockroach.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.h2console.autoconfigure.H2ConsoleProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private H2ConsoleProperties h2ConsoleProperties;

	@Test
	void contextLoads() {
	}

	@Test
	void h2DatasourceIsReachable() {
		assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class));
	}

	@Test
	void h2ConsoleIsEnabledAtTheConfiguredPath() {
		assertTrue(h2ConsoleProperties.isEnabled());
		assertEquals("/h2-console", h2ConsoleProperties.getPath());
	}

}
