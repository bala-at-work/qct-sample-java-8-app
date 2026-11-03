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

	private final File file;

	public FileSystemStorage() {
		final URL resource = getClass().getResource(STUB_RESOURCE);
		if (resource == null)
			throw new IllegalStateException("Classpath resource " + STUB_RESOURCE + " not found");
		this.file = toFile(resource);
	}

	@Override
	public Optional<FilePointer> findFile(UUID uuid) {
		log.debug("Downloading {}", uuid);
		return Optional.of(new FileSystemPointer(file));
	}

	/**
	 * Resolves a classpath resource to a file, extracting it to a temporary directory
	 * when it lives inside an archive (e.g. the executable Spring Boot jar).
	 */
	static File toFile(URL resource) {
		try {
			if ("file".equals(resource.getProtocol()))
				return new File(resource.toURI());
			final String name = Paths.get(resource.getPath().replaceAll("^.*!", "")).getFileName().toString();
			final Path dir = Files.createTempDirectory("download-server");
			final Path target = dir.resolve(name);
			try (InputStream in = resource.openStream()) {
				Files.copy(in, target);
			}
			target.toFile().deleteOnExit();
			dir.toFile().deleteOnExit();
			return target.toFile();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
