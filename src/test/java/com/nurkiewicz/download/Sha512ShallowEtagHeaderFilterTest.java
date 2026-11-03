package com.nurkiewicz.download;

import com.google.common.hash.Hashing;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha512ShallowEtagHeaderFilterTest {

	@Test
	void shouldGenerateSha512Etag() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		MockHttpServletResponse response = new MockHttpServletResponse();
		byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
		MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
			@Override
			protected void doGet(jakarta.servlet.http.HttpServletRequest req, HttpServletResponse resp) throws java.io.IOException {
				resp.getOutputStream().write(body);
			}
		});

		new Sha512ShallowEtagHeaderFilter().doFilter(request, response, chain);

		assertThat(response.getHeader("ETag")).isEqualTo("\"" + Hashing.sha512().hashBytes(body) + "\"");
		assertThat(response.getContentAsByteArray()).isEqualTo(body);
	}
}
