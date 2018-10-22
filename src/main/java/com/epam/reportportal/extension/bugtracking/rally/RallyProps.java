package com.epam.reportportal.extension.bugtracking.rally;

import com.epam.ta.reportportal.entity.integration.IntegrationParams;

import java.util.HashMap;
import java.util.Optional;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public enum RallyProps {

	PROJECT("project"),
	OAUTH_ACCESS_KEY("oauthAccessKey"),
	URL("url");

	private final String name;

	RallyProps(String name) {
		this.name = name;
	}

	public Optional<String> getParam(IntegrationParams params) {
		return Optional.ofNullable(params.getParams().get(this.name)).map(o -> (String) o);
	}

	public void setParam(IntegrationParams params, String value) {
		if (null == params.getParams()) {
			params.setParams(new HashMap<>());
		}
		params.getParams().put(this.name, value);
	}
}
