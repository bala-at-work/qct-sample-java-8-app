package com.nurkiewicz.download;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingFileTest {

	@Test
	void mapsMediaTypeWithoutCharset() {
		final MediaType result = ExistingFile.toMediaType(com.google.common.net.MediaType.parse("application/xml"));
		assertThat(result).isEqualTo(MediaType.APPLICATION_XML);
		assertThat(result.getCharset()).isNull();
	}

	@Test
	void mapsMediaTypeWithCharset() {
		final MediaType result = ExistingFile.toMediaType(com.google.common.net.MediaType.parse("text/plain; charset=utf-8"));
		assertThat(result.getType()).isEqualTo("text");
		assertThat(result.getSubtype()).isEqualTo("plain");
		assertThat(result.getCharset()).isEqualTo(StandardCharsets.UTF_8);
	}
}
