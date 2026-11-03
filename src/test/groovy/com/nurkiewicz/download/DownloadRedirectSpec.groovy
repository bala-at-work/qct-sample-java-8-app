package com.nurkiewicz.download

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import static com.google.common.net.HttpHeaders.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebAppConfiguration
@ContextConfiguration(classes = [MainApplication])
@ActiveProfiles("test")
class DownloadRedirectSpec extends Specification {

	private MockMvc mockMvc

	@Autowired
	void setWebApplicationContext(WebApplicationContext wac) {
		mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
	}

	def 'should redirect #method short URL to URL with original file name'() {
		expect:
			mockMvc
					.perform(request('/download/' + FileExamples.TXT_FILE_UUID))
					.andExpect(status().isMovedPermanently())
					.andExpect(header().string(LOCATION, '/download/' + FileExamples.TXT_FILE_UUID + '/download.txt'))
		where:
			method | request
			'GET'  | { String url -> get(url) }
			'HEAD' | { String url -> head(url) }
	}

	def 'should return 304 instead of redirect when ETag matches'() {
		expect:
			mockMvc
					.perform(get('/download/' + FileExamples.TXT_FILE_UUID).header(IF_NONE_MATCH, FileExamples.TXT_FILE.etag))
					.andExpect(status().isNotModified())
	}

	def 'should return 404 on short URL of unknown file'() {
		expect:
			mockMvc
					.perform(get('/download/' + FileExamples.NOT_FOUND_UUID))
					.andExpect(status().isNotFound())
	}

	def 'should return 400 for malformed UUID'() {
		expect:
			mockMvc
					.perform(get('/download/not-a-uuid/file.txt'))
					.andExpect(status().isBadRequest())
	}
}
