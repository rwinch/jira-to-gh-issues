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
import java.util.Map;

import lombok.Data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

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
			JiraSearchResult result = rest.getForObject(
					jiraConfig.getBaseUrl() + "/rest/api/2/search?maxResults=1000&startAt={0}&jql={jql}&fields=" +
							JiraIssue.FIELD_NAMES, JiraSearchResult.class, startAt, jql);
			results.addAll(result.getIssues());
			startAt = result.getNextStartAt();
		}

		return results;
	}

	public JiraProject findProject(String id) {
		return rest.getForObject(jiraConfig.getBaseUrl() + "/rest/api/2/project/{id}", JiraProject.class, id);
	}

	public Map<String, Object> getCommitDetail(JiraIssue issue) {
		// No official API, below is the URL used in the browser
		return rest.exchange(jiraConfig.getBaseUrl() +
				"/rest/dev-status/1.0/issue/detail?issueId={id}&applicationType=github&dataType=repository",
				HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {}, issue.getId())
				.getBody();
	}

}
