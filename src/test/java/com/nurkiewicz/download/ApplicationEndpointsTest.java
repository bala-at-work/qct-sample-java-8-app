package com.nurkiewicz.download;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationEndpointsTest {

	private static final UUID ANY_UUID = UUID.fromString("3f2504e0-4f89-41d3-9a0c-0305e82c3301");

	@Autowired
	private TestRestTemplate rest;

	@Test
	void healthIsUpWithoutDetails() {
		final ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
	}

	@Test
	void actuatorDiscoveryAndOtherEndpointsAreNotExposed() {
		for (String path : new String[]{"/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops", "/env"}) {
			assertThat(rest.getForEntity(path, String.class).getStatusCode())
					.as(path)
					.isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	@Test
	void redirectsToFileNameFromDefaultStorage() {
		final ResponseEntity<String> response = rest
				.withRedirects(ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW)
				.getForEntity("/download/" + ANY_UUID, String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
		assertThat(response.getHeaders().getLocation())
				.hasPath("/download/" + ANY_UUID + "/logback.xml");
	}

	@Test
	void servesStubFileFromDefaultStorage() {
		final ResponseEntity<String> response = rest.getForEntity("/download/" + ANY_UUID + "/logback.xml", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("<configuration>");
		assertThat(response.getHeaders().getETag()).matches("\"[0-9a-f]{128}\"");
		assertThat(response.getHeaders().getLastModified()).isPositive();
	}

	@Test
	void returnsNotModifiedForMatchingEtag() {
		final String url = "/download/" + ANY_UUID + "/logback.xml";
		final String etag = rest.getForEntity(url, String.class).getHeaders().getETag();
		final HttpHeaders headers = new HttpHeaders();
		headers.setIfNoneMatch(etag);
		final ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
	}

	@Test
	void rejectsMalformedUuid() {
		assertThat(rest.getForEntity("/download/not-a-uuid/file.txt", String.class).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}
}
