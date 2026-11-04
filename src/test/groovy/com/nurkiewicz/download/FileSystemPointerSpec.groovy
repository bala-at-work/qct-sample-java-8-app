package com.nurkiewicz.download

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class FileSystemPointerSpec extends Specification {

	private static final String FOOBAR_SHA512 = '0a50261ebd1a390fed2bf326f2673c145582a6342d523204973d0219337f81616a8069b012587cf5635f6925f1b56c360230c19b273500ee013e030601bf2425'

	@TempDir
	Path dir

	def 'ETag is the quoted SHA-512 of file contents'() {
		given:
			def pointer = new FileSystemPointer(write('a.txt', 'foobar'))
		expect:
			pointer.etag == '"' + FOOBAR_SHA512 + '"'
			pointer.matchesEtag('"' + FOOBAR_SHA512 + '"')
			!pointer.matchesEtag(FOOBAR_SHA512)
	}

	def 'ETag changes when content changes'() {
		expect:
			new FileSystemPointer(write('a.txt', 'foo')).etag != new FileSystemPointer(write('b.txt', 'bar')).etag
	}

	def 'exposes size, name and content'() {
		given:
			def pointer = new FileSystemPointer(write('a.txt', 'foobar'))
		expect:
			pointer.size == 6
			pointer.originalName == 'a.txt'
			pointer.open().withCloseable { it.text } == 'foobar'
	}

	def 'modifiedAfter is true when client time is at or after lastModified'() {
		given:
			def pointer = new FileSystemPointer(write('a.txt', 'foobar'))
			Instant lastModified = pointer.lastModified
		expect:
			pointer.modifiedAfter(lastModified)
			pointer.modifiedAfter(lastModified.plusSeconds(1))
			!pointer.modifiedAfter(lastModified.minusSeconds(1))
	}

	def 'media type is empty when undeterminable'() {
		expect:
			!new FileSystemPointer(write('noextension', 'foobar')).mediaType.isPresent()
	}

	def 'missing file is reported as UncheckedIOException'() {
		when:
			new FileSystemPointer(dir.resolve('missing.txt').toFile())
		then:
			thrown(UncheckedIOException)
	}

	private File write(String name, String content) {
		Files.writeString(dir.resolve(name), content).toFile()
	}

}
