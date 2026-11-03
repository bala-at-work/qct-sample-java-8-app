package com.nurkiewicz.download;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorExposureTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void healthShouldBeUp() {
		ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
	}

	@Test
	void discoveryPageShouldNotBeExposed() {
		assertThat(rest.getForEntity("/actuator", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void envShouldNotBeExposed() {
		assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(rest.getForEntity("/env", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
