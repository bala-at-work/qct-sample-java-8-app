package com.nurkiewicz.download

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(classes = [MainApplication], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorExposureSpec extends Specification {

	@LocalServerPort
	int port

	def 'should report health UP'() {
		when:
			HttpResponse<String> response = get('/actuator/health')
		then:
			response.statusCode() == 200
			response.body().contains('"status":"UP"')
	}

	def 'should not expose sensitive actuator endpoint #path'() {
		expect:
			get(path).statusCode() == 404
		where:
			path << ['/actuator', '/actuator/env', '/actuator/beans', '/actuator/configprops', '/actuator/mappings']
	}

	private HttpResponse<String> get(String path) {
		HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}")).GET().build(),
				HttpResponse.BodyHandlers.ofString())
	}

}
