package com.nurkiewicz.download

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class FileSystemStorageSpec extends Specification {

	@TempDir
	Path tmp

	def 'should resolve plain file resource in place'() {
		given:
			URL url = getClass().getResource('/download.txt')
		expect:
			FileSystemStorage.toFile(url) == new File(url.toURI())
	}

	def 'should extract resource packaged inside a jar'() {
		given:
			File jar = tmp.resolve('app.jar').toFile()
			new JarOutputStream(new FileOutputStream(jar)).withCloseable { out ->
				out.putNextEntry(new JarEntry('BOOT-INF/classes/logback.xml'))
				out.write('<configuration/>'.bytes)
				out.closeEntry()
			}
			URL url = new URL("jar:${jar.toURI()}!/BOOT-INF/classes/logback.xml")
		when:
			File file = FileSystemStorage.toFile(url)
		then:
			file.name == 'logback.xml'
			file.text == '<configuration/>'
	}

	def 'should serve stub file for any UUID'() {
		when:
			Optional<FilePointer> pointer = new FileSystemStorage().findFile(UUID.randomUUID())
		then:
			pointer.isPresent()
			pointer.get().originalName == 'logback.xml'
	}

}
