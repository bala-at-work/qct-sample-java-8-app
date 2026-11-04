package com.nurkiewicz.download;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.IF_MODIFIED_SINCE;
import static org.springframework.http.HttpHeaders.IF_NONE_MATCH;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;

@RestController
@RequestMapping("/download")
public class DownloadController {

	private final FileStorage storage;

	@Autowired
	public DownloadController(FileStorage storage) {
		this.storage = storage;
	}

	@RequestMapping(method = {GET, HEAD}, value = "/{uuid}")
	public ResponseEntity<Resource> redirect(
			HttpMethod method,
			@PathVariable UUID uuid,
			@RequestHeader(IF_NONE_MATCH) Optional<String> requestEtagOpt,
			@RequestHeader(IF_MODIFIED_SINCE) Optional<String> ifModifiedSinceOpt
			) {
		return findExistingFile(method, uuid)
				.map(file -> file.redirect(requestEtagOpt, parseHttpDate(ifModifiedSinceOpt)))
				.orElseGet(() -> new ResponseEntity<>(NOT_FOUND));
	}

	@RequestMapping(method = {GET, HEAD}, value = "/{uuid}/{filename}")
	public ResponseEntity<Resource> download(
			HttpMethod method,
			@PathVariable UUID uuid,
			@RequestHeader(IF_NONE_MATCH) Optional<String> requestEtagOpt,
			@RequestHeader(IF_MODIFIED_SINCE) Optional<String> ifModifiedSinceOpt
			) {
		return findExistingFile(method, uuid)
				.map(file -> file.handle(requestEtagOpt, parseHttpDate(ifModifiedSinceOpt)))
				.orElseGet(() -> new ResponseEntity<>(NOT_FOUND));
	}

	/**
	 * Invalid HTTP-dates are ignored, as required by RFC 9110 section 13.1.3.
	 */
	private static Optional<Instant> parseHttpDate(Optional<String> httpDateOpt) {
		return httpDateOpt.flatMap(httpDate -> {
			try {
				return Optional.of(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(httpDate)));
			} catch (DateTimeParseException e) {
				return Optional.empty();
			}
		});
	}

	private Optional<ExistingFile> findExistingFile(HttpMethod method, UUID uuid) {
		return storage
				.findFile(uuid)
				.map(pointer -> new ExistingFile(method, pointer, uuid));
	}

}
