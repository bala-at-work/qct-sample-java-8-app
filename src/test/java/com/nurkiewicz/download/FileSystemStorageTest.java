package com.nurkiewicz.download;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemStorageTest {

	@Test
	void shouldReturnLogbackStubForAnyUuid() throws Exception {
		FilePointer pointer = new FileSystemStorage().findFile(UUID.randomUUID()).orElseThrow();

		assertThat(pointer.getOriginalName()).isEqualTo("logback.xml");
		assertThat(pointer.getSize()).isPositive();
		try (InputStream in = pointer.open()) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("<configuration>");
		}
	}
}
