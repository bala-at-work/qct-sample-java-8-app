package com.nurkiewicz.download

import com.google.common.hash.Hashing
import spock.lang.Specification

class FileSystemPointerSpec extends Specification {

	def 'should use quoted SHA-512 of file content as ETag'() {
		given:
			File file = new File(getClass().getResource('/download.txt').toURI())
			String expected = '"' + Hashing.sha512().hashBytes(file.bytes) + '"'
		expect:
			new FileSystemPointer(file).etag == expected
	}

}
