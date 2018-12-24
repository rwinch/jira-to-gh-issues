/*
 * Copyright 2002-2018 the original author or authors.
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
package io.pivotal.migration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraUser;


/**
 * Small utility to gather information from Jira to help prepare for the migration:
 *
 * <p>Estimate how many backport issues would have to be created if each
 * backport is to be represented with a separate ticket. Note that that question
 * also depends on which version is chosen to the milestone for a ticket.
 * For example given fix versions 5.1 RC2, 5.0.8, 4.3.19, picking 5.0.8 as the
 * milestone as opposed to 5.1 RC2 results in a substantially lower number of
 * backport issues (~2500 vs ~1000 for SPR with a total of 17K+ issues).
 * See {@link JiraIssue#initFixAndBackportVersions()}.
 *
 * <p>Generate a list of assignees from the Jira tickets, which can be used as an
 * aid in filling out src/main/resources/jira-to-github-users.properties.
 *
 * @author Rossen Stoyanchev
 */
public class InfoGatheringApp {

	private static final String TARGET_ISSUES_JQL = "project = SPR ORDER BY key ASC";


	public static void main(String args[]) {

		JiraConfig config = new JiraConfig();
		config.setBaseUrl("https://jira.spring.io");

		JiraClient client = new JiraClient(config);
		List<JiraIssue> issues = client.findIssues(TARGET_ISSUES_JQL);

		System.out.println("Estimated backport issue count: " + estimateBackportCount(issues) + " \n\n");
		System.out.println("Assignees: \n" + collectAssignees(issues) + "\n\n");
	}

	private static Integer estimateBackportCount(List<JiraIssue> issues) {
		int count = 0;
		for (JiraIssue issue : issues) {
			count += issue.getBackportVersions().size();
		}
		return count;
	}

	private static List<String> collectAssignees(List<JiraIssue> issues) {
		Map<String, AtomicInteger> result = new HashMap<>();
		for (JiraIssue issue : issues) {
			JiraUser user = issue.getFields().getAssignee();
			if (user != null) {
				String key = user.getKey() + " (" + user.getDisplayName() + ")";
				result.computeIfAbsent(key, s -> new AtomicInteger(0)).getAndIncrement();
			}
		}
		return result.entrySet().stream()
				.map(entry -> entry.getKey() + " [" + entry.getValue().get() + "]")
				.collect(Collectors.toList());
	}

}
