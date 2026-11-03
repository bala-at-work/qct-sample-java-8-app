package com.nurkiewicz.download;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.UUID;

public class FileExamples {

	// Fixed values: Groovy 4 inlines these constants into the specs at compile time.
	public static final UUID TXT_FILE_UUID = UUID.fromString("5d8a9a4e-63a2-4b9e-9a4b-3f1f2c8d7e10");
	public static final FilePointer TXT_FILE = txtFile();
	public static final UUID NOT_FOUND_UUID = UUID.fromString("0b6c3f62-2a8e-4f4e-8f0a-9c2d1e7b4a55");

	private static FileSystemPointer txtFile() {
		final URL resource = FileStorageStub.class.getResource("/download.txt");
		if (resource == null) {
			throw new IllegalStateException("Test resource /download.txt not found");
		}
		try {
			return new FileSystemPointer(new File(resource.toURI()));
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}


}
