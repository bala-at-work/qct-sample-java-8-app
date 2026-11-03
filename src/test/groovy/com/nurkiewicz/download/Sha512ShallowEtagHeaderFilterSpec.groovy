package com.nurkiewicz.download

import com.google.common.hash.Hashing
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class Sha512ShallowEtagHeaderFilterSpec extends Specification {

	def 'should set SHA-512 based ETag on GET response'() {
		given:
			def request = new MockHttpServletRequest('GET', '/anything')
			def response = new MockHttpServletResponse()
			FilterChain chain = { req, res -> res.outputStream.write('foobar'.getBytes(StandardCharsets.UTF_8)) } as FilterChain
		when:
			new Sha512ShallowEtagHeaderFilter().doFilter(request, response, chain)
		then:
			response.getHeader('ETag') == '"' + Hashing.sha512().hashString('foobar', StandardCharsets.UTF_8) + '"'
			response.contentAsString == 'foobar'
	}
}
