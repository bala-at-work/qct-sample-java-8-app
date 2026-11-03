package com.nurkiewicz.download;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingFileTest {

	@Test
	void shouldMapMediaTypeWithoutCharset() {
		MediaType result = ExistingFile.toMediaType(com.google.common.net.MediaType.parse("text/plain"));

		assertThat(result).isEqualTo(MediaType.TEXT_PLAIN);
		assertThat(result.getCharset()).isNull();
	}

	@Test
	void shouldMapMediaTypeWithCharset() {
		MediaType result = ExistingFile.toMediaType(com.google.common.net.MediaType.parse("application/xml; charset=utf-8"));

		assertThat(result.getType()).isEqualTo("application");
		assertThat(result.getSubtype()).isEqualTo("xml");
		assertThat(result.getCharset()).isEqualTo(StandardCharsets.UTF_8);
	}
}
