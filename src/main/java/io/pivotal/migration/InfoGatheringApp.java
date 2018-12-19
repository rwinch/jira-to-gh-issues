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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraUser;
import io.pivotal.util.MarkdownEngine;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


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
 * <p>Search uses of "@" that look like user mentions by checking against the
 * list of Jira users. The generated list should be further edited manually to filter
 * out anything that doesn't look like a person's name (e.g. @Bean), and pasted
 * into src/main/resources/user-mentions-to-escape.txt so that such mentions can
 * be escaped and avoid notifications to unrelated GitHub users. In reality
 * anything prefixed with "@" is likely to collide with some GH user id but we
 * can only do so much to avoid such noise, and if a GH user id does not look
 * like a person's name, or is too short, then it's probably not uncommon to get
 * mentioned by accident. If anyone complains we can further edit the tickets
 * that have the mentions.
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

		System.out.println("\nLoaded " + issues.size() + " matching issues\n");

		Integer backportCount = estimatePotentialBackports(issues);
		Map<String, AtomicInteger> assignees = collectAssignees(issues);
		MultiValueMap<String, String> mentions = collectPotentialMentions(issues);

		System.out.println("Estimated backport issue count: " +
				backportCount + " (if creating an individual ticket per backport)\n");

		System.out.println(assignees.entrySet().stream()
				.map(entry -> entry.getKey() + " [" + entry.getValue().get() + "]")
				.collect(Collectors.joining("\n", "Assignees: \n", "\n")));

		System.out.println(mentions.keySet().stream().collect(Collectors.joining(",", "mentions=", "\n")));

		System.out.println(mentions.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining("\n", "Mentions by ticket:\n", "\n")));
	}

	private static Integer estimatePotentialBackports(List<JiraIssue> issues) {
		int count = 0;
		for (JiraIssue issue : issues) {
			count += issue.getBackportVersions().size();
		}
		return count;
	}

	private static Map<String, AtomicInteger> collectAssignees(List<JiraIssue> issues) {
		Map<String, AtomicInteger> result = new HashMap<>();
		for (JiraIssue issue : issues) {
			JiraUser user = issue.getFields().getAssignee();
			if (user != null) {
				String key = user.getKey() + " (" + user.getDisplayName() + ")";
				result.computeIfAbsent(key, s -> new AtomicInteger(0)).getAndIncrement();
			}
		}
		return result;
	}

	private static MultiValueMap<String, String> collectPotentialMentions(List<JiraIssue> issues) {

		// First scrape user hints from Jira user names
		Set<JiraUser> users = new HashSet<>();
		for (JiraIssue issue : issues) {
			users.add(issue.getFields().getReporter());
			for (JiraComment comment : issue.getFields().getComment().getComments()) {
				users.add(comment.getAuthor());
			}
		}
		Set<String> userHints = users.stream()
				.flatMap(user -> Stream.of(user.getKey().toLowerCase(), user.getDisplayName().toLowerCase()))
				.collect(Collectors.toSet());

		// Now search for mentions that match the user hints
		MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
		for (JiraIssue issue : issues) {
			for (JiraComment comment : issue.getFields().getComment().getComments()) {
				Matcher matcher = MarkdownEngine.ghMentionPattern.matcher(comment.getBody());
				while (matcher.find()) {
					String match = matcher.group(2);
					String searchKey = match.substring(1).toLowerCase();
					userHints.stream()
							.filter(userHint -> userHint.contains(searchKey))
							.findAny()
							.ifPresent(userHint -> {
								List<String> issueKeys = result.get(match);
								if (issueKeys == null) {
									result.add(match, issue.getKey());
								}
								else if (!issueKeys.contains(issue.getKey())) {
									issueKeys.add(issue.getKey());
								}
							});
				}
			}
		}
		return result;
	}

}
