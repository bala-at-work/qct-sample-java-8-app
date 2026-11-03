package com.nurkiewicz.download

import org.springframework.http.MediaType
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ExistingFileSpec extends Specification {

	def 'should convert media type without charset'() {
		when:
			MediaType result = ExistingFile.toMediaType(com.google.common.net.MediaType.parse('text/plain'))
		then:
			result == new MediaType('text', 'plain')
			result.charset == null
	}

	def 'should convert media type preserving charset'() {
		when:
			MediaType result = ExistingFile.toMediaType(com.google.common.net.MediaType.parse('text/html; charset=utf-8'))
		then:
			result == new MediaType('text', 'html', StandardCharsets.UTF_8)
	}

}
