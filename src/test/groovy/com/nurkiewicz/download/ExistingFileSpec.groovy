package com.nurkiewicz.download

import org.springframework.http.MediaType
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ExistingFileSpec extends Specification {

	def 'converts Guava media type with charset to Spring media type'() {
		expect:
			ExistingFile.toMediaType(com.google.common.net.MediaType.parse('text/plain; charset=utf-8')) ==
					new MediaType('text', 'plain', StandardCharsets.UTF_8)
	}

	def 'converts Guava media type without charset to Spring media type'() {
		when:
			MediaType result = ExistingFile.toMediaType(com.google.common.net.MediaType.parse('application/pdf'))
		then:
			result == MediaType.APPLICATION_PDF
			result.charset == null
	}
}
