package com.nurkiewicz.download

import com.google.common.net.MediaType as GuavaMediaType
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import spock.lang.Specification

import java.time.Instant

class ExistingFileSpec extends Specification {

	def 'should map Guava media type #guavaType to Spring content type #expected'() {
		given:
			FilePointer pointer = Stub(FilePointer) {
				getEtag() >> '"tag"'
				getSize() >> 6L
				getLastModified() >> Instant.parse('2020-01-01T00:00:00Z')
				getMediaType() >> Optional.ofNullable(guavaType == null ? null : GuavaMediaType.parse(guavaType))
			}
		when:
			def response = new ExistingFile(HttpMethod.HEAD, pointer, UUID.randomUUID()).handle(Optional.empty(), Optional.empty())
		then:
			response.headers.getContentType() == expected
			response.headers.getContentLength() == 6L
			response.headers.getETag() == '"tag"'
		where:
			guavaType                    || expected
			'text/plain'                 || MediaType.TEXT_PLAIN
			'text/plain; charset=utf-8'  || MediaType.parseMediaType('text/plain;charset=UTF-8')
			'application/xml'            || MediaType.APPLICATION_XML
			null                         || null
	}
}
