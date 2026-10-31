package com.nurkiewicz.download

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorExposureSpec extends Specification {

	@Autowired
	MockMvc mockMvc

	def 'health endpoint is exposed without component details'() {
		expect:
			mockMvc
					.perform(get('/actuator/health'))
					.andExpect(status().isOk())
					.andExpect(content().json('{"status":"UP"}', true))
	}

	@Unroll
	def 'sensitive actuator endpoint #path is not exposed'() {
		expect:
			mockMvc
					.perform(get(path))
					.andExpect(status().isNotFound())
		where:
			path << ['/actuator', '/actuator/env', '/actuator/beans', '/actuator/configprops',
					 '/actuator/mappings', '/actuator/heapdump', '/actuator/threaddump', '/actuator/loggers']
	}
}
