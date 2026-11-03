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
	private static final String STUB_RESOURCE = "/logback.xml";

	private volatile File stubFile;

	@Override
	public Optional<FilePointer> findFile(UUID uuid) {
		log.debug("Downloading {}", uuid);
		final FileSystemPointer pointer = new FileSystemPointer(stubFile());
		return Optional.of(pointer);
	}

	private File stubFile() {
		if (stubFile == null) {
			synchronized (this) {
				if (stubFile == null) {
					stubFile = resolveStubFile();
				}
			}
		}
		return stubFile;
	}

	/**
	 * Inside a packaged jar the resource is not a plain file, so it is copied to a temporary file once.
	 */
	private File resolveStubFile() {
		final URL resource = getClass().getResource(STUB_RESOURCE);
		if (resource == null) {
			throw new IllegalStateException("Classpath resource " + STUB_RESOURCE + " not found");
		}
		try {
			if ("file".equals(resource.getProtocol())) {
				return Paths.get(resource.toURI()).toFile();
			}
			final Path dir = Files.createTempDirectory("download-server");
			final Path copy = dir.resolve(Paths.get(STUB_RESOURCE).getFileName());
			try (InputStream in = resource.openStream()) {
				Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
			}
			copy.toFile().deleteOnExit();
			dir.toFile().deleteOnExit();
			return copy.toFile();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

}
