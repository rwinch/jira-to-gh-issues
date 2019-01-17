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

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.pre.BaseApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.util.Assert;

/**
 * @author Rossen Stoyanchev
 */
public class LastJiraCommentApp extends BaseApp {

	private static final Logger logger = LogManager.getLogger(LastJiraCommentApp.class);

	private static final String START_OF_COMMENT =
			"The Spring Framework has moved from Jira to GitHub Issues. ";


	public static void main(String[] args) throws IOException {

		JiraConfig config = initJiraConfig();
		JiraClient client = new JiraClient(config);

		String repoSlug = props.getProperty("github.repository-slug");
		String issueBaseUrl = "https://github.com/" + repoSlug + "/issues/";

		File mappingsFile = new File("github-issue-mappings.properties");
		Map<String, Integer> issueMappings = loadIssueMappings(mappingsFile);

		List<JiraIssue> issues = client.findIssues(config.getMigrateJql())
				.stream()
				.filter(issue -> {
					if (!issueMappings.containsKey(issue.getKey())) {
						logger.warn("No mapping for issue {}", issue.getKey());
						return false; // restricted issue perhaps that wasn't migrated
					}
					List<JiraComment> comments = issue.getFields().getComment().getComments();
					if (comments.stream().anyMatch(c -> c.getBody().startsWith(START_OF_COMMENT))) {
						logger.warn("Comment already exists for issue {}", issue.getKey());
						return false; // restricted issue perhaps that wasn't migrated
					}
					return true;
				})
				.collect(Collectors.toList());

		Map<String, String> commentsToAdd = new LinkedHashMap<>(issues.size());
		issues.forEach(issue -> {
			Integer targetId = issueMappings.get(issue.getKey());
			Assert.notNull(targetId, "No mapping for issue " + issue.getKey());
			String body = START_OF_COMMENT;
			body += "This issue was migrated to " +
					"[spring-projects/spring-framework#" + targetId + "|" + issueBaseUrl + targetId + "]. ";
			if (issue.getFields().getResolution() == null) {
				body += "Please visit the GitHub issue to view further activity, add comments, " +
						"or subscribe to receive notifications.";
			}
			commentsToAdd.put(issue.getKey(), body);
		});

		client.addComments(commentsToAdd);
	}

}
