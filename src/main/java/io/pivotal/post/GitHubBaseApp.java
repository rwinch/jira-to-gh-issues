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
package io.pivotal.post;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.pivotal.github.GitHubRestTemplate;
import io.pivotal.pre.BaseApp;
import io.pivotal.util.RateLimitHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 * @author Rossen Stoyanchev
 */
public class GitHubBaseApp extends BaseApp {

	protected static final Logger logger = LogManager.getLogger(GitHubBaseApp.class);


	protected static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};

	protected static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE =
			new ParameterizedTypeReference<List<Map<String, Object>>>() {};

	protected static final MediaType APPLICATION_GH_RAW_JSON = new MediaType("application", "vnd.github.3.raw+json");


	protected static final RestTemplate rest = new GitHubRestTemplate(new RateLimitHelper(), logger);

	protected static final String repositorySlug = props.getProperty("github.repository-slug");

	protected static final String accessToken = props.getProperty("github.access-token");


	protected static UriComponents issuesUric = UriComponentsBuilder
			.fromUriString("https://api.github.com/repos/" + repositorySlug + "/issues").encode().build();

	protected static UriComponents issueUric = UriComponentsBuilder.newInstance()
			.uriComponents(issuesUric).path("/{issueId}").encode().build();

	protected static UriComponents commentsUricBuilder = UriComponentsBuilder.newInstance()
			.uriComponents(issueUric).path("/comments").encode().build();

	protected static UriComponents commentUricBuilder = UriComponentsBuilder.newInstance()
			.uriComponents(issuesUric).path("/comments/{commentId}").encode().build();



	protected static <T> T exchange(RequestEntity<?> requestEntity, Class<T> responseType,
			FileWriter writer, AtomicBoolean failed) {

		return exchange(requestEntity, ParameterizedTypeReference.forType(responseType), writer, failed);
	}

	protected static <T> T exchange(RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType,
			FileWriter writer, AtomicBoolean failed) {

		try {
			return rest.exchange(requestEntity, responseType).getBody();
		}
		catch (Throwable ex) {
			if (failed != null) {
				failed.set(true);
			}
			String line = "Failed to write " + requestEntity.getUrl() + ": " + ex.getMessage() + "\n";
			try {
				writer.write(line);
				writer.flush();
			}
			catch (IOException ioEx) {
				logger.error("Failed to write the below error result due to \"{}\":\n{}", ex.getMessage(), line);
			}
		}
		return null;
	}


}
