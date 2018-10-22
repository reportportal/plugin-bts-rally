package com.epam.reportportal.extension.bugtracking.rally;

import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.GetRequest;
import com.rallydev.rest.response.GetResponse;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class RallyStrategyTest {
	static final String URI = "https://rally1.rallydev.com";

	@Test
	public void getTicketTest() throws URISyntaxException, IOException {
		RallyRestApi api = new RallyRestApi(new URI(URI), apiKey);
		GetRequest rq = new GetRequest("/defect/189075388172");
		GetResponse rs = api.get(rq);
		JsonObject object = rs.getObject();
		System.out.println();
	}

}