package com.nurkiewicz.download

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.UrlResource
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class FileSystemStorageSpec extends Specification {

	@TempDir
	Path tempDir

	def 'should serve the bundled stub file for any UUID'() {
		given:
			FileStorage storage = new FileSystemStorage()
		when:
			FilePointer pointer = storage.findFile(UUID.randomUUID()).get()
		then:
			pointer.originalName == 'logback.xml'
			pointer.size > 0
			pointer.open().withCloseable { it.text.contains('<configuration>') }
	}

	def 'should use a file-system resource directly'() {
		given:
			ClassPathResource resource = new ClassPathResource('download.txt')
		expect:
			FileSystemStorage.toFile(resource) == resource.getFile()
	}

	def 'should serve a resource packaged inside a jar'() {
		given:
			Path jar = tempDir.resolve('app.jar')
			new JarOutputStream(Files.newOutputStream(jar)).withCloseable { out ->
				out.putNextEntry(new JarEntry('BOOT-INF/classes/logback.xml'))
				out.write('<configuration/>'.bytes)
				out.closeEntry()
			}
			UrlResource resource = new UrlResource("jar:${jar.toUri()}!/BOOT-INF/classes/logback.xml")
			FileStorage storage = new FileSystemStorage(resource)
		when:
			FilePointer pointer = storage.findFile(UUID.randomUUID()).get()
		then:
			!resource.isFile()
			pointer.originalName == 'logback.xml'
			pointer.size == '<configuration/>'.bytes.length
			pointer.open().withCloseable { it.text } == '<configuration/>'
	}
}
