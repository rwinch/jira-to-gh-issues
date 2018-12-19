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
package io.pivotal.migration;

import java.util.List;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraProject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * @author Rob Winch
 * @see InfoGatheringApp
 */
@SpringBootApplication(scanBasePackages = "io.pivotal")
public class MigrationApp implements CommandLineRunner {

	@Autowired
	JiraClient jira;

	@Autowired
	MigrationClient github;

	@Autowired
	JiraConfig jiraConfig;


	public static void main(String args[]) {
		SpringApplication.run(MigrationApp.class);
	}


	@Override
	public void run(String... strings) throws Exception {

		try {
			// Delete if github.delete-create-repository-slug=true AND 0 commits
			github.deleteRepository();
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
				throw ex;
			}
		}

		github.createRepository();

//		github.createLabels();

//		JiraProject project = jira.findProject(jiraConfig.getProjectId());
//		github.createMilestones(project.getVersions());

		List<JiraIssue> issues = jira.findIssuesVotesAndCommits(jiraConfig.getMigrateJql());

		github.createIssues(issues);

		System.exit(0);
	}

}
