/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal.github;

import java.io.IOException;
import java.net.HttpURLConnection;

import io.pivotal.util.RateLimitHelper;
import org.apache.logging.log4j.Logger;
import org.eclipse.egit.github.core.client.GitHubClient;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * @author Rossen Stoyanchev
 */
public class ExtendedEgitGitHubClient extends GitHubClient {

	private final RateLimitHelper rateLimitHelper;

	private final Logger logger;


	public ExtendedEgitGitHubClient(RateLimitHelper rateLimitHelper, Logger logger) {
		this.rateLimitHelper = rateLimitHelper;
		this.logger = logger;
	}

	@Override
	protected HttpURLConnection configureRequest(HttpURLConnection request) {
		logger.debug("{} {}", request.getRequestMethod().toUpperCase(), request.getURL().getPath());
		rateLimitHelper.obtainPermitToCall();
		HttpURLConnection result = super.configureRequest(request);
		result.setRequestProperty(HEADER_ACCEPT, MediaType.APPLICATION_JSON_VALUE);
		return result;
	}

	@Override
	protected GitHubClient updateRateLimits(HttpURLConnection request) {
		GitHubClient client = super.updateRateLimits(request);
		logger.debug("{} {X-RateLimit-Remaining:{}}", getHttpStatus(request), getRemainingRequests());
		return client;
	}

	private HttpStatus getHttpStatus(HttpURLConnection request) {
		try {
			int code = request.getResponseCode();
			return HttpStatus.valueOf(code);
		}
		catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}
}
