package com.nurkiewicz.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
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

	private static final String STUB_RESOURCE = "/logback.xml";

	private volatile File stubFile;

	@Override
	public Optional<FilePointer> findFile(UUID uuid) {
		log.debug("Downloading {}", uuid);
		return Optional.of(new FileSystemPointer(stubFile()));
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
	 * When running from a packaged jar the classpath resource is not a plain file,
	 * so it is copied to a temporary file once.
	 */
	private File resolveStubFile() {
		final URL resource = getClass().getResource(STUB_RESOURCE);
		if (resource == null) {
			throw new IllegalStateException("Classpath resource " + STUB_RESOURCE + " not found");
		}
		if ("file".equals(resource.getProtocol())) {
			return new File(resource.getFile());
		}
		try (InputStream in = resource.openStream()) {
			final Path tmpDir = Files.createTempDirectory("download-server");
			final Path target = tmpDir.resolve(new File(STUB_RESOURCE).getName());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			target.toFile().deleteOnExit();
			tmpDir.toFile().deleteOnExit();
			return target.toFile();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
