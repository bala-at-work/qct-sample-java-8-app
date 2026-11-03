package com.nurkiewicz.download

import spock.lang.Specification

import java.security.MessageDigest

class FileSystemPointerSpec extends Specification {

	def 'should use quoted SHA-512 of file content as ETag'() {
		given:
			File file = new File(getClass().getResource('/download.txt').toURI())
			String expectedHash = MessageDigest.getInstance('SHA-512').digest(file.bytes).encodeHex().toString()
		when:
			FileSystemPointer pointer = new FileSystemPointer(file)
		then:
			pointer.etag == '"' + expectedHash + '"'
			pointer.matchesEtag('"' + expectedHash + '"')
			!pointer.matchesEtag('"other"')
			pointer.size == file.length()
			pointer.originalName == 'download.txt'
	}
}
