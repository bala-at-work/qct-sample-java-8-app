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

	private static final String STUB_RESOURCE = "logback.xml";

	private volatile File stubFile;

	@Override
	public Optional<FilePointer> findFile(UUID uuid) {
		log.debug("Downloading {}", uuid);
		return Optional.of(new FileSystemPointer(stubFile()));
	}

	/**
	 * The classpath resource lives inside the jar when packaged, so it is copied to a real file once.
	 */
	private File stubFile() {
		if (stubFile == null) {
			synchronized (this) {
				if (stubFile == null) {
					stubFile = copyResourceToTempFile();
				}
			}
		}
		return stubFile;
	}

	private File copyResourceToTempFile() {
		try (InputStream input = getClass().getResourceAsStream("/" + STUB_RESOURCE)) {
			if (input == null) {
				throw new IllegalStateException("Classpath resource /" + STUB_RESOURCE + " not found");
			}
			final Path dir = Files.createTempDirectory("download-server");
			final Path target = dir.resolve(STUB_RESOURCE);
			Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
			target.toFile().deleteOnExit();
			dir.toFile().deleteOnExit();
			return target.toFile();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
