package com.nurkiewicz.download;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DownloadControllerRedirectTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getShouldRedirectToOriginalName() throws Exception {
		mockMvc.perform(get("/download/" + FileExamples.TXT_FILE_UUID))
				.andExpect(status().isMovedPermanently())
				.andExpect(header().string("Location", "/download/" + FileExamples.TXT_FILE_UUID + "/download.txt"));
	}

	@Test
	void headShouldRedirectToOriginalName() throws Exception {
		mockMvc.perform(head("/download/" + FileExamples.TXT_FILE_UUID))
				.andExpect(status().isMovedPermanently())
				.andExpect(header().string("Location", "/download/" + FileExamples.TXT_FILE_UUID + "/download.txt"));
	}

	@Test
	void redirectShouldReturn404ForUnknownUuid() throws Exception {
		mockMvc.perform(get("/download/" + FileExamples.NOT_FOUND_UUID))
				.andExpect(status().isNotFound());
	}

	@Test
	void shouldEmitLastModifiedHeader() throws Exception {
		mockMvc.perform(head("/download/" + FileExamples.TXT_FILE_UUID + "/download.txt"))
				.andExpect(status().isOk())
				.andExpect(header().dateValue("Last-Modified", FileExamples.TXT_FILE.getLastModified().toEpochMilli() / 1000 * 1000));
	}

	@Test
	void shouldReturn400ForMalformedUuid() throws Exception {
		mockMvc.perform(get("/download/not-a-uuid/file.txt"))
				.andExpect(status().isBadRequest());
	}
}
