package com.nurkiewicz.download;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemStorageTest {

	@Test
	void returnsReadableStubFileForAnyUuid() {
		final FileSystemStorage storage = new FileSystemStorage();

		final FilePointer first = storage.findFile(UUID.randomUUID()).orElseThrow();
		final FilePointer second = storage.findFile(UUID.randomUUID()).orElseThrow();

		assertThat(first.getOriginalName()).isEqualTo("logback.xml");
		assertThat(first.getSize()).isPositive();
		assertThat(first.getEtag()).isEqualTo(second.getEtag());
	}
}
