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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * @author Rob Winch
 * @author Rossen Stoyanchev
 */
@Data
@Component
public class JiraClient {

	private static final Logger logger = LogManager.getLogger(JiraClient.class);

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	@Autowired
	JiraConfig jiraConfig;

	RestOperations rest = new RestTemplate();


	public JiraProject findProject(String id) {
		return rest.getForObject(getBaseUrl() + "/project/{id}", JiraProject.class, id);
	}

	private String getBaseUrl() {
		return jiraConfig.getBaseUrl() + "/rest/api/2";
	}

	public List<JiraIssue> findIssues(String jql) {

		logger.info("Loading issues, query: \"{}\"", jql);

		List<JiraIssue> issues = new ArrayList<>();
		Long startAt = 0L;
		while(startAt != null) {
			JiraSearchResult searchResult = rest.getForObject(
					getBaseUrl() + "/search?maxResults=1000&startAt={0}&jql={jql}&fields=" + JiraIssue.FIELD_NAMES,
					JiraSearchResult.class, startAt, jql);
			issues.addAll(searchResult.getIssues());
			startAt = searchResult.getNextStartAt();
		}

		logger.info("{} issues loaded ", issues.size());

		issues.forEach(JiraIssue::initFixAndBackportVersions);
		return issues;
	}

	public List<JiraIssue> findIssuesVotesAndCommits(String jql) {
		List<JiraIssue> issues = findIssues(jql);
		logger.info("Loading votes and commits");
		issues.forEach(issue -> {
			issue.setVotes(getVotes(issue));
			issue.setCommitUrls(getCommitUrls(issue));
		});
		return issues;
	}

	private int getVotes(JiraIssue jiraIssue) {
		Map<String, Object> result = rest.exchange(
				getBaseUrl() + "/issue/{id}/votes", HttpMethod.GET, null, MAP_TYPE, jiraIssue.getId()).getBody();
		return (int) result.get("votes");
	}

	@SuppressWarnings("unchecked")
	private List<String> getCommitUrls(JiraIssue issue) {

		// No official API, below is the URL used in the browser
		Map<String, Object> result = rest.exchange(jiraConfig.getBaseUrl() +
						"/rest/dev-status/1.0/issue/detail?issueId={id}&applicationType=github&dataType=repository",
				HttpMethod.GET, null, MAP_TYPE, issue.getId()).getBody();

		List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("detail");
		if (!details.isEmpty()) {
			List<Map<String, Object>> repos = (List<Map<String, Object>>) details.get(0).get("repositories");
			if (!repos.isEmpty()) {
				List<Map<String, Object>> commits = (List<Map<String, Object>>) repos.get(0).get("commits");
				return commits.stream().map(map -> (String) map.get("url")).collect(Collectors.toList());
			}
		}
		return Collections.emptyList();
	}

}
