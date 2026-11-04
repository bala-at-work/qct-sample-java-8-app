package com.nurkiewicz.download

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static com.google.common.net.HttpHeaders.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebAppConfiguration
@ContextConfiguration(classes = [MainApplication])
@ActiveProfiles("test")
class DownloadControllerSpec extends Specification {

	private MockMvc mockMvc

	// Not final: Groovy would inline the compile-time UUID instead of the runtime FileExamples value
	private static String TEXT_FILE = '/download/' + FileExamples.TXT_FILE_UUID + '/file.txt';
	private static String SHORT_URL = '/download/' + FileExamples.TXT_FILE_UUID;
	private static final String FOOBAR_SHA512 = '0a50261ebd1a390fed2bf326f2673c145582a6342d523204973d0219337f81616a8069b012587cf5635f6925f1b56c360230c19b273500ee013e030601bf2425'

	@Autowired
	public void setWebApplicationContext(WebApplicationContext wac) {
		mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
	}

	def 'should return bytes of existing file'() {
		expect:
			mockMvc
					.perform(
						get(TEXT_FILE))
					.andExpect(status().isOk())
					.andExpect(content().string("foobar"))
	}

	def 'should return 404 and no content'() {
		expect:
			mockMvc
					.perform(
						get('/download/' + FileExamples.NOT_FOUND_UUID + "/file.txt"))
					.andExpect(status().isNotFound())
					.andExpect(content().bytes(new byte[0]))
	}

	def 'should send file if ETag not present'() {
		expect:
			mockMvc
					.perform(
						get(TEXT_FILE))
					.andExpect(
						status().isOk())
		}

	def 'should send file if ETag present but not matching'() {
		expect:
			mockMvc
					.perform(
						get(TEXT_FILE)
								.header(IF_NONE_MATCH, '"WHATEVER"'))
					.andExpect(
						status().isOk())
	}

	def 'should not send file if ETag matches content'() {
		given:
			String etag = FileExamples.TXT_FILE.getEtag()
		expect:
			mockMvc
					.perform(
						get(TEXT_FILE)
								.header(IF_NONE_MATCH, etag))
					.andExpect(
						status().isNotModified())
					.andExpect(
						header().string(ETAG, etag))
	}

	def 'should not return file if wasn\'t modified recently'() {
		given:
			Instant lastModified = FileExamples.TXT_FILE.getLastModified()
			String dateHeader = toDateHeader(lastModified)
		expect:
			mockMvc
					.perform(
					get(TEXT_FILE)
							.header(IF_MODIFIED_SINCE, dateHeader))
					.andExpect(
							status().isNotModified())
	}

	def 'should not return file if server has older version than the client'() {
		given:
			Instant lastModifiedLaterThanServer = FileExamples.TXT_FILE.getLastModified().plusSeconds(60)
			String dateHeader = toDateHeader(lastModifiedLaterThanServer)
		expect:
			mockMvc
					.perform(
					get(TEXT_FILE)
							.header(IF_MODIFIED_SINCE, dateHeader))
					.andExpect(
							status().isNotModified())
	}

	def 'should return file if was modified after last retrieval'() {
		given:
			Instant lastModifiedRecently = FileExamples.TXT_FILE.getLastModified().minusSeconds(60)
			String dateHeader = toDateHeader(lastModifiedRecently)
		expect:
			mockMvc
					.perform(
					get(TEXT_FILE)
							.header(IF_MODIFIED_SINCE, dateHeader))
					.andExpect(
							status().isOk())
	}

	def 'should return 200 OK on HEAD request, but without body'() {
		expect:
			mockMvc
					.perform(
						head(TEXT_FILE))
					.andExpect(
							status().isOk())
					.andExpect(
							content().bytes(new byte[0]))
	}

	def 'should return 304 on HEAD request if we have cached version'() {
		expect:
			mockMvc
				.perform(
					head(TEXT_FILE)
							.header(IF_NONE_MATCH, FileExamples.TXT_FILE.getEtag()))
				.andExpect(
					status().isNotModified())
				.andExpect(
					header().string(ETAG, FileExamples.TXT_FILE.getEtag()))
	}

	def 'should return Content-length header'() {
		expect:
			mockMvc
					.perform(
					head(TEXT_FILE))
					.andExpect(
					status().isOk())
					.andExpect(
					header().longValue(CONTENT_LENGTH, FileExamples.TXT_FILE.size))
	}

	def 'should return content type in response'() {
		expect:
			mockMvc
					.perform(
					head(TEXT_FILE))
					.andExpect(
					status().isOk())
					.andExpect(
					header().string(CONTENT_TYPE, "text/plain"))
	}

	def 'should redirect short URL to canonical URL'() {
		expect:
			mockMvc
					.perform(
					get(SHORT_URL))
					.andExpect(
					status().isMovedPermanently())
					.andExpect(
					header().string(LOCATION, SHORT_URL + '/download.txt'))
	}

	def 'should redirect on HEAD of short URL'() {
		expect:
			mockMvc
					.perform(
					head(SHORT_URL))
					.andExpect(
					status().isMovedPermanently())
					.andExpect(
					header().string(LOCATION, SHORT_URL + '/download.txt'))
					.andExpect(
					content().bytes(new byte[0]))
	}

	def 'should return 304 on short URL when client cache valid'() {
		expect:
			mockMvc
					.perform(
					get(SHORT_URL)
							.header(IF_NONE_MATCH, FileExamples.TXT_FILE.getEtag()))
					.andExpect(
					status().isNotModified())
	}

	def 'should return 404 on short URL for unknown UUID'() {
		expect:
			mockMvc
					.perform(
					get('/download/' + FileExamples.NOT_FOUND_UUID))
					.andExpect(
					status().isNotFound())
	}

	def 'should keep SHA-512 content ETag unchanged'() {
		expect:
			mockMvc
					.perform(
					head(TEXT_FILE))
					.andExpect(
					header().string(ETAG, '"' + FOOBAR_SHA512 + '"'))
	}

	def 'should reject malformed UUID'() {
		expect:
			mockMvc
					.perform(
					get(path))
					.andExpect(
					status().isBadRequest())
		where:
			path << ['/download/not-a-uuid', '/download/not-a-uuid/file.txt']
	}

	def 'should reject unmapped verb on #path'() {
		expect:
			mockMvc
					.perform(request)
					.andExpect(
					status().isMethodNotAllowed())
		where:
			[path, request] << [[SHORT_URL, TEXT_FILE], [{ post(it) }, { put(it) }, { delete(it) }]]
					.combinations()
					.collect { p, builder -> [p, builder(p)] }
	}

	def 'should ignore unparseable If-Modified-Since'() {
		expect:
			mockMvc
					.perform(
					get(TEXT_FILE)
							.header(IF_MODIFIED_SINCE, 'garbage'))
					.andExpect(
					status().isOk())
					.andExpect(
					content().string("foobar"))
	}

	private static String toDateHeader(Instant lastModified) {
		ZonedDateTime dateTime = ZonedDateTime.ofInstant(lastModified, ZoneOffset.UTC)
		DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime)
	}

}
