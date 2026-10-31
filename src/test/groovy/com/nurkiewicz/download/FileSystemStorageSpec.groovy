package com.nurkiewicz.download

import spock.lang.Specification

class FileSystemStorageSpec extends Specification {

	def 'serves the stub resource from a real file regardless of how the classpath is packaged'() {
		given:
			FileSystemStorage storage = new FileSystemStorage()
			byte[] expected = FileSystemStorage.getResourceAsStream('/' + FileSystemStorage.STUB_RESOURCE).bytes
		when:
			FilePointer pointer = storage.findFile(UUID.randomUUID()).get()
		then:
			pointer.originalName == FileSystemStorage.STUB_RESOURCE
			pointer.size == expected.length
			pointer.open().withCloseable { it.bytes } == expected
			pointer.etag ==~ /"[0-9a-f]{128}"/
	}

	def 'returns the same stub file for every UUID'() {
		given:
			FileSystemStorage storage = new FileSystemStorage()
		expect:
			storage.findFile(UUID.randomUUID()).get().etag == storage.findFile(UUID.randomUUID()).get().etag
	}
}
