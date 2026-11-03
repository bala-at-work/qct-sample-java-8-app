package com.nurkiewicz.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

	private final File file;

	public FileSystemStorage() {
		this(new ClassPathResource("logback.xml"));
	}

	FileSystemStorage(Resource resource) {
		this.file = toFile(resource);
	}

	@Override
	public Optional<FilePointer> findFile(UUID uuid) {
		log.debug("Downloading {}", uuid);
		return Optional.of(new FileSystemPointer(file));
	}

	/**
	 * Resources packaged inside a jar have no {@link File} representation,
	 * so they are copied to a temporary file, keeping the original file name.
	 */
	static File toFile(Resource resource) {
		try {
			if (resource.isFile()) {
				return resource.getFile();
			}
			final Path dir = Files.createTempDirectory("download-server");
			final Path target = dir.resolve(resource.getFilename());
			try (InputStream in = resource.getInputStream()) {
				Files.copy(in, target);
			}
			dir.toFile().deleteOnExit();
			target.toFile().deleteOnExit();
			return target.toFile();
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot access " + resource, e);
		}
	}

}
