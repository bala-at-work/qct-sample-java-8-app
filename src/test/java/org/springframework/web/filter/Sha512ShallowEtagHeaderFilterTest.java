package org.springframework.web.filter;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha512ShallowEtagHeaderFilterTest {

	private final Sha512ShallowEtagHeaderFilter filter = new Sha512ShallowEtagHeaderFilter();
	private final byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
	private final String hash = Hashing.sha512().hashBytes(body).toString();

	@Test
	void generatesStrongSha512Etag() throws Exception {
		assertThat(filter.generateETagHeaderValue(new ByteArrayInputStream(body), false))
				.isEqualTo("\"" + hash + "\"");
	}

	@Test
	void generatesWeakSha512Etag() throws Exception {
		assertThat(filter.generateETagHeaderValue(new ByteArrayInputStream(body), true))
				.isEqualTo("W/\"" + hash + "\"");
	}
}
