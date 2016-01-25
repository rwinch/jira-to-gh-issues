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
package io.pivotal;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import io.pivotal.github.GithubClient;
import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraProject;

/**
 * @author Rob Winch
 *
 */
@SpringBootApplication
public class Migrate implements CommandLineRunner {

	@Autowired
	JiraClient jira;

	@Autowired
	GithubClient github;

	@Autowired
	JiraConfig jiraConfig;

	public static void main(String args[]) {
		SpringApplication.run(Migrate.class);
	}

	@Override
	public void run(String... strings) throws Exception {
		try {
			github.deleteRepository();
		}catch(HttpClientErrorException e) {
			if(e.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
				throw e;
			}
		}
		System.out.println("Creating a test repository to place the issues in");
		github.createRepository();

		System.out.println("Finding the project info");
		JiraProject project = jira.findProject(getJiraProjectId());

		System.out.println("Creating Milestones");
		github.createMilestones(project.getVersions());
		System.out.println("Creating Labels");
		github.createComponentLabels(project.getComponents());
		github.createIssueTypeLabels(project.getIssueTypes());

		System.out.println("Getting JIRA issues");
		List<JiraIssue> issues = jira.findIssues(jiraConfig.getMigrateJql());
		System.out.println("Found "+issues.size()+ " JIRA issues to migrate");

		System.out.println("Creating issues");
		github.createIssues(issues);

	}

	private String getJiraProjectId() {
		return jiraConfig.getProjectId();
	}

}
