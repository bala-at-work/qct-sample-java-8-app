package com.nurkiewicz.download

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes = MainApplication)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorExposureSpec extends Specification {

	@Autowired
	MockMvc mockMvc

	def 'should expose health without details'() {
		expect:
			mockMvc.perform(get('/actuator/health'))
					.andExpect(status().isOk())
					.andExpect(jsonPath('$.status').value('UP'))
					.andExpect(jsonPath('$.components').doesNotExist())
	}

	@Unroll
	def 'should not expose #path'() {
		expect:
			mockMvc.perform(get(path))
					.andExpect(status().isNotFound())
		where:
			path << ['/actuator', '/actuator/env', '/actuator/beans', '/actuator/configprops', '/actuator/mappings']
	}
}
