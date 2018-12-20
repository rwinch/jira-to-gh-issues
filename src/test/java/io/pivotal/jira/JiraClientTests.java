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


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.jodatime.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import io.pivotal.jira.JiraIssue.Fields;

/**
 * @author Rob Winch
 * @author Rossen Stoyanchev
 */
public class JiraClientTests {
	private JiraConfig jiraConfig;
	private JiraClient client;

	@Before
	public void setup() {
		jiraConfig = new JiraConfig();
		jiraConfig.setBaseUrl("https://jira-stage.spring.io");
		jiraConfig.setProjectId("SEC");
		client = new JiraClient(jiraConfig);
		client.setJiraConfig(jiraConfig);
	}

	@Test
	public void findAllSec() {
		List<JiraIssue> issues = client.findIssues("project = SEC");
		assertThat(issues.size()).isGreaterThanOrEqualTo(3138);
	}

	@Test
	public void findAllSpr() {
		jiraConfig.setProjectId("SPR");
		List<JiraIssue> issues = client.findIssues(jiraConfig.getMigrateJql());
		assertThat(issues.size()).isGreaterThanOrEqualTo(16000);
		List<String> pairs = new ArrayList<>();
		for (int i=1; i < issues.size(); i++) {
			String keyA = issues.get(i - 1).getKey();
			String keyB = issues.get(i).getKey();
			if (Long.parseLong(keyA.substring("SPR-".length())) > Long.parseLong(keyB.substring("SPR-".length()))) {
				pairs.add(keyA + "-" + keyB);
			}
		}
		assertThat(pairs).isEmpty();
	}

	@Test
	public void findIssuesSec1() {
		List<JiraIssue> issues = client.findIssues("issue = SEC-1");

		assertThat(issues).hasSize(1);

		JiraIssue jiraIssue = issues.get(0);
		assertThat(jiraIssue.getKey()).isEqualTo("SEC-1");
		Fields fields = jiraIssue.getFields();
		assertThat(fields.getSummary()).isEqualTo("web.xml to Acegi Security Migration Tool");
		assertThat(fields.getDescription()).isEqualTo("As discussed with Luke Taylor, generate a new tool that converts a web.xml file containing security constraints into an Acegi Security application context XML file. Designed to provide a simple migration for people, but with the more advanced features such as remember-me and anonymous authentication enabled by default.");
		JiraIssueType issuetype = fields.getIssuetype();
		assertThat(issuetype.getId()).isEqualTo(2);
		assertThat(issuetype.getName()).isEqualTo("New Feature");
		assertThat(fields.getCreated()).isEqualTo("2005-06-21T20:31:15.000+0000");
		assertThat(fields.getUpdated()).isEqualTo("2016-02-06T06:09:14.000+0000");

		List<JiraComment> comments = fields.getComment().getComments();
		assertThat(comments).hasSize(5);
		JiraComment first = comments.get(0);
		assertThat(first.getAuthor().getKey()).isEqualTo("balex");
		assertThat(first.getAuthor().getDisplayName()).isEqualTo("Ben Alex");
		assertThat(first.getBody()).isEqualTo("This is an important and useful tool to get in 0.9.0, so it can receive some exposure and testing.");
		assertThat(first.getCreated()).isEqualTo("2005-06-26T15:39:42.000+0000");

		assertThat(fields.getFixVersions()).hasSize(1);
		assertThat(fields.getFixVersions().get(0).getName()).isEqualTo("0.9.0");

		assertThat(fields.getStatus().getName()).isEqualTo("Closed");

		assertThat(fields.getResolution().getName()).isEqualTo("Complete");

		JiraUser reporter = fields.getReporter();
		assertThat(reporter.getKey()).isEqualTo("balex");
		assertThat(reporter.getDisplayName()).isEqualTo("Ben Alex");
		JiraUser assignee = fields.getAssignee();
		assertThat(assignee.getKey()).isEqualTo("luke");
		assertThat(assignee.getDisplayName()).isEqualTo("Luke Taylor");
	}

	@Test
	public void findProjectSec() {

		JiraProject project = client.findProject("SEC");

		List<JiraVersion> versions = project.getVersions();
		assertThat(versions).hasSize(82);
		assertThat(versions).extracting(JiraVersion::getName).contains("0.9.0","4.1.0 M1");
		assertThat(project.getIssueTypes()).extracting(JiraIssueType::getName).contains("Bug","New Feature","Task");
		assertThat(project.getComponents()).extracting(JiraComponent::getName).contains("ACLs","Test","Core");
	}

	@Test
	public void votesAndCommits() {
		List<JiraIssue> issues = client.findIssuesVotesAndCommits("id = SPR-6373", jiraIssues -> jiraIssues);

		assertThat(issues).hasSize(1);
		JiraIssue issue = issues.get(0);
		assertThat(issue.getVotes()).isEqualTo(54);
		List<String> commitUrls = issue.getCommitUrls();
		assertThat(commitUrls).hasSize(2);
	}

	@Test
	public void fixVersionShouldIgnoreDevelopmentVersion() {
		List<JiraIssue> issues = client.findIssues("id = SPR-17178");

		assertThat(issues).hasSize(1);
		JiraIssue issue = issues.get(0);

		List<String> all = issue.getFields().getFixVersions().stream().map(JiraFixVersion::getName).collect(Collectors.toList());
		assertThat(all).contains("5.1 RC2", "5.0.9", "4.3.19").hasSize(3);

		String fixedIn = issue.getFixVersion().getName();
		assertThat(fixedIn).isEqualTo("5.0.9");

		List<String> backportedTo = issue.getBackportVersions().stream().map(JiraFixVersion::getName).collect(Collectors.toList());
		assertThat(backportedTo).contains("4.3.19").hasSize(1);
	}

	@Test
	public void backportVersionsShouldNotIgnoreDevelopmentVersions() {
		List<JiraIssue> issues = client.findIssues("id = SPR-17129");

		assertThat(issues).hasSize(1);
		JiraIssue issue = issues.get(0);

		List<String> all = issue.getFields().getFixVersions().stream().map(JiraFixVersion::getName).collect(Collectors.toList());
		assertThat(all).contains("5.1 RC3", "5.1 GA").hasSize(2);

		assertThat(issue.getFixVersion().getName()).isEqualTo("5.1 GA");
		assertThat(issue.getBackportVersions()).isEmpty();
	}

	@Test
	public void backportIssueType() {
		List<JiraIssue> issues = client.findIssues("id in (SPR-10996, SPR-11009)");

		assertThat(issues).hasSize(2);
		JiraIssue issue = issues.stream().filter(i -> i.getKey().equals("SPR-10996")).findAny().get();

		List<String> all = issue.getFields().getFixVersions().stream().map(JiraFixVersion::getName).collect(Collectors.toList());
		assertThat(all).contains("4.0 RC1").hasSize(1);

		assertThat(issue.getFixVersion().getName()).isEqualTo("3.2.5");
		assertThat(issue.getBackportVersions()).isEmpty();
	}

}
