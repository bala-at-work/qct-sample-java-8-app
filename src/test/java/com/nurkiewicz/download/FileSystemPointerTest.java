package com.nurkiewicz.download;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemPointerTest {

	@Test
	void shouldUseQuotedSha512OfContentAsEtag() {
		String expected = "\"" + Hashing.sha512().hashString("foobar", StandardCharsets.UTF_8) + "\"";

		assertThat(FileExamples.TXT_FILE.getEtag()).isEqualTo(expected);
		assertThat(FileExamples.TXT_FILE.matchesEtag(expected)).isTrue();
		assertThat(FileExamples.TXT_FILE.matchesEtag("\"other\"")).isFalse();
	}

	@Test
	void shouldExposeFileMetadataAndContent() throws Exception {
		FilePointer pointer = FileExamples.TXT_FILE;

		assertThat(pointer.getOriginalName()).isEqualTo("download.txt");
		assertThat(pointer.getSize()).isEqualTo(6);
		try (InputStream in = pointer.open()) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("foobar");
		}
	}
}
