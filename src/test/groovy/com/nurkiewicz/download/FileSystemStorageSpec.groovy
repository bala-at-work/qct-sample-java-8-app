package com.nurkiewicz.download

import spock.lang.Specification

class FileSystemStorageSpec extends Specification {

	def 'should serve stub logback.xml for any UUID'() {
		given:
			FileSystemStorage storage = new FileSystemStorage()
		when:
			FilePointer pointer = storage.findFile(UUID.randomUUID()).get()
		then:
			pointer.originalName == 'logback.xml'
			pointer.size > 0
			pointer.open().withCloseable { it.text.contains('<configuration>') }
	}
}
