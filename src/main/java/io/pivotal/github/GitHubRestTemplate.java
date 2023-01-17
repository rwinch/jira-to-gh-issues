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
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import io.pivotal.util.RateLimitHelper;
import org.apache.logging.log4j.Logger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * @author Rossen Stoyanchev
 */
public class GitHubRestTemplate extends RestTemplate {

	private static final List<String> rateLimitedMethods = Arrays.asList("POST", "PATCH", "PUT", "DELETE");


	private final RateLimitHelper rateLimitHelper;

	private final Logger logger;


	public GitHubRestTemplate(RateLimitHelper rateLimitHelper, Logger logger) {
		super(new HttpComponentsClientHttpRequestFactory());
		this.rateLimitHelper = rateLimitHelper;
		this.logger = logger;
	}


	@Override
	protected <T> T doExecute(URI url, HttpMethod method, RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor) throws RestClientException {
		return doExecuteExtended(url, method, requestCallback, responseExtractor);
	}

	private <T> T doExecuteExtended(URI url, HttpMethod method, RequestCallback requestCallback,
			ResponseExtractor<T> responseExtractor) throws RestClientException {

		try {
			RequestCallback decoratedRequestCallback = request -> {
				if (requestCallback != null) {
					requestCallback.doWithRequest(request);
				}
				logger.debug("{} {}", method, url.getPath());
			};
			if (rateLimitedMethods.contains(method.name())) {
				rateLimitHelper.obtainPermitToCall();
			}
			return super.doExecute(url, method, decoratedRequestCallback, responseExtractor);
		}
		catch (HttpClientErrorException ex) {
			HttpHeaders headers = ex.getResponseHeaders();
			String requestInfo = method + " " + url.getPath() + " " + headers;
			if (headers == null) {
				logger.error("No headers for " + requestInfo);
				throw ex;
			}
			long timeToSleep;
			String retryAfter = headers.getFirst("Retry-After");
			if (retryAfter != null) {
				logger.debug("Received Retry-After: " + retryAfter + " for " + requestInfo);
				timeToSleep = 1000 * Integer.parseInt(retryAfter);
			}
			else if ("0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
				String reset = headers.getFirst("X-RateLimit-Reset");
				if (reset != null) {
					logger.debug("Received X-RateLimit-Reset: " + reset + " for " + requestInfo);
					timeToSleep = (1000 * Long.parseLong(reset)) - System.currentTimeMillis();
				}
				else {
					logger.error("X-RateLimit-Remaining:0 but no X-RateLimit-Reset: " + requestInfo);
					throw ex;
				}
			}
			else {
				throw ex;
			}
			try {
				if (timeToSleep > 0) {
					Thread.sleep(timeToSleep);
				}
			}
			catch (InterruptedException interruptedEx) {
				// Ignore
			}
		}
		// Recurse and retry...
		return doExecuteExtended(url, method, requestCallback, responseExtractor);
	}

	@Override
	protected void handleResponse(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
		logger.debug("{} {X-RateLimit-Remaining:{}}",
				response.getStatusCode(), response.getHeaders().getFirst("X-RateLimit-Remaining"));
		super.handleResponse(url, method, response);
	}
}
