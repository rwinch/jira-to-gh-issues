/*
 * Copyright 2002-2023 the original author or authors.
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
package io.pivotal.pre;

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
 * Extract the full list of assignees from Jira tickets. This can be used to prepare
 * src/main/resources/jira-to-github-users.properties.
 *
 * @author Rossen Stoyanchev
 * @author Artem Bilan
 */
public class AssigneesReport extends BaseApp {


	public static void main(String[] args) {

		JiraConfig config = initJiraConfig();
		JiraClient client = new JiraClient(config);

		Map<String, AtomicInteger> result = new HashMap<>();
		for (JiraIssue issue : client.findIssues(config.getMigrateJql())) {
			JiraUser user = issue.getFields().getAssignee();
			if (user != null) {
				String key = user.getKey() + " (" + user.getDisplayName() + ")";
				result.computeIfAbsent(key, s -> new AtomicInteger(0)).getAndIncrement();
			}
		}

		String assignees = result.entrySet().stream()
				.map(entry -> entry.getKey() + " [" + entry.getValue().get() + "]")
				.collect(Collectors.joining(System.lineSeparator()));

		System.out.println("Assignees: \n" + assignees);
	}

}
