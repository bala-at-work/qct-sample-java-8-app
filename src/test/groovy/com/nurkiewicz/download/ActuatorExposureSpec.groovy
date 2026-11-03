package com.nurkiewicz.download

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(classes = [MainApplication], webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorExposureSpec extends Specification {

	@LocalServerPort
	int port

	private final HttpClient client = HttpClient.newHttpClient()

	def 'should expose health endpoint reporting UP without details'() {
		when:
			HttpResponse<String> response = get('/actuator/health')
		then:
			response.statusCode() == 200
			response.body() == '{"status":"UP"}'
	}

	def 'should not expose actuator endpoint #path'() {
		expect:
			get(path).statusCode() == 404
		where:
			path << ['/actuator', '/actuator/env', '/actuator/beans', '/actuator/configprops', '/actuator/mappings', '/actuator/info']
	}

	private HttpResponse<String> get(String path) {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}")).GET().build()
		client.send(request, HttpResponse.BodyHandlers.ofString())
	}
}
