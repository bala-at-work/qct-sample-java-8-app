package com.nurkiewicz.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * Stub implementation that for any UUID returns `logback.xml` file.
 * Replace with real implementation.
 */
@Component
@Profile("!test")
public class FileSystemStorage implements FileStorage {

	private static final Logger log = LoggerFactory.getLogger(FileSystemStorage.class);

	private static final String STUB_RESOURCE = "logback.xml";

	private final File stubFile = resolveStubFile();

	@Override
	public Optional<FilePointer> findFile(UUID uuid) {
		log.debug("Downloading {}", uuid);
		final FileSystemPointer pointer = new FileSystemPointer(stubFile);
		return Optional.of(pointer);
	}

	/**
	 * Resolves the classpath resource to a file, extracting it when it lives inside a JAR.
	 */
	private File resolveStubFile() {
		final URL resource = getClass().getResource("/" + STUB_RESOURCE);
		if (resource == null) {
			throw new IllegalStateException("Missing classpath resource " + STUB_RESOURCE);
		}
		try {
			if ("file".equals(resource.getProtocol())) {
				return Paths.get(resource.toURI()).toFile();
			}
			final Path dir = Files.createTempDirectory("download-server");
			final Path extracted = dir.resolve(STUB_RESOURCE);
			try (InputStream in = resource.openStream()) {
				Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
			}
			extracted.toFile().deleteOnExit();
			dir.toFile().deleteOnExit();
			return extracted.toFile();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

}
