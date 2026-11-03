package com.nurkiewicz.download;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemPointerTest {

	@Test
	void computesSha512EtagAndMetadata(@TempDir Path dir) throws IOException {
		final Path file = dir.resolve("sample.txt");
		Files.writeString(file, "foobar");

		final FileSystemPointer pointer = new FileSystemPointer(file.toFile());

		final String expectedHash = Hashing.sha512().hashString("foobar", StandardCharsets.UTF_8).toString();
		assertThat(pointer.getEtag()).isEqualTo("\"" + expectedHash + "\"");
		assertThat(pointer.matchesEtag("\"" + expectedHash + "\"")).isTrue();
		assertThat(pointer.matchesEtag("\"other\"")).isFalse();
		assertThat(pointer.getSize()).isEqualTo(6);
		assertThat(pointer.getOriginalName()).isEqualTo("sample.txt");
		try (InputStream in = pointer.open()) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("foobar");
		}
	}

	@Test
	void modifiedAfterIsTrueWhenClientCopyIsCurrent(@TempDir Path dir) throws IOException {
		final File file = Files.writeString(dir.resolve("a.txt"), "x").toFile();
		final FileSystemPointer pointer = new FileSystemPointer(file);

		assertThat(pointer.modifiedAfter(pointer.getLastModified())).isTrue();
		assertThat(pointer.modifiedAfter(pointer.getLastModified().plusSeconds(1))).isTrue();
		assertThat(pointer.modifiedAfter(pointer.getLastModified().minusSeconds(1))).isFalse();
	}
}
