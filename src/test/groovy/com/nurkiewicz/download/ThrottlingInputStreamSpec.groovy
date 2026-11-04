package com.nurkiewicz.download

import com.google.common.util.concurrent.RateLimiter
import spock.lang.Specification

class ThrottlingInputStreamSpec extends Specification {

	private final RateLimiter rateLimiter = Mock()

	def 'read() acquires one permit per byte'() {
		given:
			def stream = new ThrottlingInputStream(new ByteArrayInputStream([1, 2] as byte[]), rateLimiter)
		when:
			def first = stream.read()
			def second = stream.read()
		then:
			2 * rateLimiter.acquire(1)
			first == 1
			second == 2
	}

	def 'read(byte[]) acquires array length permits'() {
		given:
			def stream = new ThrottlingInputStream(new ByteArrayInputStream(new byte[2048]), rateLimiter)
		when:
			def read = stream.read(new byte[1024])
		then:
			1 * rateLimiter.acquire(1024)
			read == 1024
	}

	def 'read(byte[], int, int) acquires len permits'() {
		given:
			def stream = new ThrottlingInputStream(new ByteArrayInputStream(new byte[2048]), rateLimiter)
		when:
			def read = stream.read(new byte[1024], 0, 512)
		then:
			1 * rateLimiter.acquire(512)
			read == 512
	}

	def 'reads full content unchanged'() {
		given:
			byte[] content = 'foobar'.bytes
			def stream = new ThrottlingInputStream(new ByteArrayInputStream(content), RateLimiter.create(ExistingFile.ONE_MB))
		expect:
			stream.readAllBytes() == content
	}

	def 'propagates target IOException unwrapped'() {
		given:
			InputStream target = Mock()
			target.read() >> { throw new IOException('boom') }
			def stream = new ThrottlingInputStream(target, rateLimiter)
		when:
			stream.read()
		then:
			def e = thrown(IOException)
			e.message == 'boom'
	}

	def 'throttle rate stays at 1 MB per second'() {
		expect:
			ExistingFile.ONE_MB == 1024L * 1024L
	}

}
