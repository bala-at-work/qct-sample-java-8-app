package com.nurkiewicz.download

import spock.lang.Specification

class FileSystemStorageSpec extends Specification {

	def 'stub storage serves logback.xml for any UUID'() {
		given:
			def storage = new FileSystemStorage()
		when:
			def pointer = storage.findFile(UUID.randomUUID())
		then:
			pointer.isPresent()
			pointer.get().originalName == 'logback.xml'
			pointer.get().size > 0
	}

}
