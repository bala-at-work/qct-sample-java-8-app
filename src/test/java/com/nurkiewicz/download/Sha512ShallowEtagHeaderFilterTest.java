package com.nurkiewicz.download;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha512ShallowEtagHeaderFilterTest {

	@Test
	void setsSha512EtagForResponseBody() throws Exception {
		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final MockFilterChain chain = new MockFilterChain(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.getOutputStream().write("foobar".getBytes(StandardCharsets.UTF_8));
			}
		});

		new Sha512ShallowEtagHeaderFilter().doFilter(request, response, chain);

		final String expected = "\"" + Hashing.sha512().hashString("foobar", StandardCharsets.UTF_8) + "\"";
		assertThat(response.getHeader("ETag")).isEqualTo(expected);
		assertThat(response.getContentAsString()).isEqualTo("foobar");
	}
}
