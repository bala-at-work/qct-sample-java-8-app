package com.nurkiewicz.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

	static final String STUB_RESOURCE = "logback.xml";

	private final File stubFile;

	public FileSystemStorage() {
		this.stubFile = extractToFileSystem(STUB_RESOURCE);
	}

	@Override
	public Optional<FilePointer> findFile(UUID uuid) {
		log.debug("Downloading {}", uuid);
		return Optional.of(new FileSystemPointer(stubFile));
	}

	/**
	 * Classpath resources are not plain files when running from the Spring Boot fat jar,
	 * so copy the resource to a temporary directory once and serve it from there.
	 */
	private static File extractToFileSystem(String resourceName) {
		try (InputStream in = FileSystemStorage.class.getResourceAsStream("/" + resourceName)) {
			if (in == null) {
				throw new IllegalStateException("Classpath resource not found: " + resourceName);
			}
			final Path dir = Files.createTempDirectory("download-server");
			dir.toFile().deleteOnExit();
			final Path target = dir.resolve(resourceName);
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			target.toFile().deleteOnExit();
			return target.toFile();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
