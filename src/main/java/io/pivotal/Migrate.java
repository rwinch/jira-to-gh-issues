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

import io.pivotal.github.GithubClient;
import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraProject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * @author Rob Winch
 *
 */
@SpringBootApplication
public class Migrate implements CommandLineRunner {

	static final Logger logger = LogManager.getLogger(Migrate.class);


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
			// Delete if github.delete-create-repository-slug=true AND 0 commits
			github.deleteRepository();
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
				throw ex;
			}
		}

		github.createRepository();

		github.createLabels();

		JiraProject project = jira.findProject(jiraConfig.getProjectId());
		github.createMilestones(project.getVersions());

		List<JiraIssue> issues = jira.findIssuesVotesAndCommits(jiraConfig.getMigrateJql());

		github.createIssues(issues);
	}

}
