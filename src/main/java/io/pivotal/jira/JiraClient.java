/*
 * Copyright 2002-2016 the original author or authors.
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
package io.pivotal.jira;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import lombok.Data;

/**
 * @author Rob Winch
 *
 */
@Data
@Component
public class JiraClient {
	@Autowired
	JiraConfig jiraConfig;

	RestOperations rest = new RestTemplate();

	public List<JiraIssue> findIssues(String jql) {

		List<JiraIssue> results = new ArrayList<>();
		Long startAt = 0L;

		while(startAt != null) {
			ResponseEntity<JiraSearchResult> result = rest.getForEntity(
					jiraConfig.getBaseUrl() + "/rest/api/2/search?maxResults=1000&startAt={0}&jql={jql}&fields=" +
							JiraIssue.FIELD_NAMES, JiraSearchResult.class, startAt, jql);
			JiraSearchResult body = result.getBody();
			results.addAll(body.getIssues());
			startAt = body.getNextStartAt();
		}

		return results;
	}

	public JiraProject findProject(String id) {
		ResponseEntity<JiraProject> result = rest.getForEntity(jiraConfig.getBaseUrl() + "/rest/api/2/project/{id}", JiraProject.class, id);
		return result.getBody();
	}
}
